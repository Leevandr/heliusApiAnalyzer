package ru.levandr.heliusapianalyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.levandr.heliusapianalyzer.model.InstructionData;
import ru.levandr.heliusapianalyzer.model.RaydiumSwapTransaction;
import ru.levandr.heliusapianalyzer.model.TokenTransfer;
import ru.levandr.heliusapianalyzer.model.entity.RaydiumPool;
import ru.levandr.heliusapianalyzer.repository.RaydiumPoolRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с пулами Raydium
 * Отвечает за обработку и сохранение информации о пулах
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaydiumPoolService {
    private static final String RAYDIUM_PROGRAM_ID = "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8";
    private static final int MIN_POOL_DATA_LENGTH = 192; // Минимальная длина данных пула
    private static final int AUTHORITY_LENGTH = 32; // Длина поля authority
    private static final int STATUS_LENGTH = 1;    // Длина поля status
    private static final int TOKEN_MINT_LENGTH = 32; // Длина адреса токена
    private static final BigDecimal MAX_PRICE_CHANGE = BigDecimal.valueOf(0.2); // 20% максимальное изменение цены

    private final RaydiumPoolRepository poolRepository;
    private final SolanaRpcService solanaRpcService;

    /**
     * Обрабатывает информацию о пуле из транзакции свопа
     */
    @Transactional
    public void processPoolFromSwap(RaydiumSwapTransaction swapTx) {
        if (swapTx == null || swapTx.getSignature() == null) {
            log.warn("Received invalid swap transaction");
            return;
        }

        try {
            String poolAddress = extractPoolAddress(swapTx);
            if (poolAddress == null) {
                return;
            }

            log.info("Processing pool {} from transaction {}", poolAddress, swapTx.getSignature());

            // Получаем или создаем пул
            RaydiumPool pool = getOrCreatePool(poolAddress);

            // Проверяем и обновляем токены пула
            if (!updatePoolTokens(pool, swapTx)) {
                log.warn("Failed to update tokens for pool {}", poolAddress);
                return;
            }

            // Обновляем ликвидность и цену
            if (!updatePoolLiquidity(pool)) {
                log.warn("Failed to update liquidity for pool {}", poolAddress);
                handlePoolUpdateError(pool);
            }

            // Обновляем объем торгов
            updateVolume24h(pool, swapTx);

            // Проверяем изменение цены
            if (pool.getPrice() != null && isPriceChangeValid(pool)) {
                // Если все проверки прошли успешно, сохраняем пул
                pool.setLastUpdate(LocalDateTime.now());
                RaydiumPool savedPool = poolRepository.save(pool);
                logPoolUpdate(savedPool);
            } else {
                log.warn("Invalid price change detected for pool {}", poolAddress);
            }

            if (pool.getLiquidityA() != null && pool.getLiquidityB() != null) {
                TokenTransfer transfer = swapTx.getTokenTransfers().get(0);
                BigDecimal amount = BigDecimal.valueOf(transfer.getTokenAmount());
                boolean isAtoB = pool.getTokenAMint().equals(transfer.getMint());

                try {
                    BigDecimal slippage = calculateExpectedSlippage(pool, amount, isAtoB);
                    log.info("Slippage for swap {}: {}%", swapTx.getSignature(), slippage);
                } catch (Exception e) {
                    log.warn("Could not calculate slippage: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error processing pool from swap tx {}: {}",
                    swapTx.getSignature(), e.getMessage(), e);
        }
    }

    /**
     * Получает существующий или создает новый пул
     */
    private RaydiumPool getOrCreatePool(String poolAddress) {
        return poolRepository.findById(poolAddress)
                .orElseGet(() -> {
                    RaydiumPool newPool = new RaydiumPool();
                    newPool.setAddress(poolAddress);
                    newPool.setActive(true);
                    newPool.setLastUpdate(LocalDateTime.now());
                    return newPool;
                });
    }

    /**
     * Проверяет валидность изменения цены
     */
    private boolean isPriceChangeValid(RaydiumPool pool) {
        Optional<RaydiumPool> oldPool = poolRepository.findById(pool.getAddress());
        if (oldPool.isEmpty() || oldPool.get().getPrice() == null) {
            return true;
        }

        BigDecimal oldPrice = oldPool.get().getPrice();
        BigDecimal newPrice = pool.getPrice();
        BigDecimal priceChange = newPrice.subtract(oldPrice).abs()
                .divide(oldPrice, 8, RoundingMode.HALF_UP);

        return priceChange.compareTo(MAX_PRICE_CHANGE) <= 0;
    }

    /**
     * Обновляет информацию о токенах пула
     */
    private boolean updatePoolTokens(RaydiumPool pool, RaydiumSwapTransaction swapTx) {
        List<TokenTransfer> transfers = swapTx.getTokenTransfers();
        if (transfers == null || transfers.size() < 2) {
            log.warn("Invalid token transfers in transaction {}", swapTx.getSignature());
            return false;
        }

        TokenTransfer firstTransfer = transfers.get(0);
        TokenTransfer secondTransfer = transfers.get(1);

        if (firstTransfer.getMint() == null || secondTransfer.getMint() == null) {
            log.warn("Missing token mints in transaction {}", swapTx.getSignature());
            return false;
        }

        // Обновляем только если токены еще не установлены или изменились
        if (pool.getTokenAMint() == null || pool.getTokenBMint() == null ||
                !pool.getTokenAMint().equals(firstTransfer.getMint()) ||
                !pool.getTokenBMint().equals(secondTransfer.getMint())) {

            pool.setTokenAMint(firstTransfer.getMint());
            pool.setTokenBMint(secondTransfer.getMint());
            log.info("Updated tokens for pool {}: A={}, B={}",
                    pool.getAddress(), firstTransfer.getMint(), secondTransfer.getMint());
        }

        return true;
    }

    /**
     * Обновляет данные о ликвидности пула
     */
    private boolean updatePoolLiquidity(RaydiumPool pool) {
        try {
            byte[] accountData = solanaRpcService.getPoolAccountData(pool.getAddress());
            if (!validatePoolData(accountData)) {
                return false;
            }

            ByteBuffer buffer = ByteBuffer.wrap(accountData);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Пропускаем authority и status
            buffer.position(AUTHORITY_LENGTH + STATUS_LENGTH);

            // Пропускаем адреса токенов
            buffer.position(buffer.position() + 2 * TOKEN_MINT_LENGTH);

            // Читаем резервы
            BigDecimal reserveA = BigDecimal.valueOf(buffer.getLong());
            BigDecimal reserveB = BigDecimal.valueOf(buffer.getLong());

            // Проверяем валидность резервов
            if (!validateReserves(reserveA, reserveB)) {
                return false;
            }

            // Обновляем данные пула
            updatePoolData(pool, reserveA, reserveB);
            return true;

        } catch (Exception e) {
            log.error("Error updating liquidity for pool {}: {}",
                    pool.getAddress(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Проверяет валидность данных пула
     */
    private boolean validatePoolData(byte[] data) {
        if (data == null || data.length < MIN_POOL_DATA_LENGTH) {
            log.warn("Invalid pool data length: expected >= {}, got {}",
                    MIN_POOL_DATA_LENGTH, data != null ? data.length : 0);
            return false;
        }
        return true;
    }

    /**
     * Проверяет валидность резервов
     */
    private boolean validateReserves(BigDecimal reserveA, BigDecimal reserveB) {
        if (reserveA == null || reserveB == null ||
                reserveA.compareTo(BigDecimal.ZERO) < 0 ||
                reserveB.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Invalid reserves: A={}, B={}", reserveA, reserveB);
            return false;
        }
        return true;
    }

    /**
     * Обновляет данные пула
     */
    private void updatePoolData(RaydiumPool pool, BigDecimal reserveA, BigDecimal reserveB) {
        pool.setLiquidityA(reserveA);
        pool.setLiquidityB(reserveB);

        if (reserveB.compareTo(BigDecimal.ZERO) > 0) {
            pool.setPrice(reserveA.divide(reserveB, 8, RoundingMode.HALF_UP));
        }

        log.info("Updated pool data: A={}, B={}, price={}",
                reserveA, reserveB, pool.getPrice());
    }

    /**
     * Обновляет объем торгов за 24 часа
     */
    private void updateVolume24h(RaydiumPool pool, RaydiumSwapTransaction swapTx) {
        if (swapTx.getTokenTransfers() == null || swapTx.getTokenTransfers().isEmpty()) {
            return;
        }

        TokenTransfer transfer = swapTx.getTokenTransfers().get(0);
        if (transfer.getTokenAmount() == null || transfer.getTokenAmount() <= 0) {
            log.warn("Invalid token amount in transfer");
            return;
        }

        BigDecimal amount = BigDecimal.valueOf(transfer.getTokenAmount());
        BigDecimal currentVolume = pool.getVolume24h() != null ?
                pool.getVolume24h() : BigDecimal.ZERO;

        pool.setVolume24h(currentVolume.add(amount));
        log.debug("Updated volume: +{} = {}", amount, pool.getVolume24h());
    }

    /**
     * Логирует обновление пула
     */
    private void logPoolUpdate(RaydiumPool pool) {
        log.info("Updated pool {}: A={}, B={}, price={}, volume={}",
                pool.getAddress(),
                pool.getLiquidityA(),
                pool.getLiquidityB(),
                pool.getPrice(),
                pool.getVolume24h());
    }

    /**
     * Обрабатывает ошибки обновления пула
     */
    private void handlePoolUpdateError(RaydiumPool pool) {
        // TODO: Добавить счетчик ошибок и деактивировать пул после N ошибок
        pool.setActive(false);
        log.warn("Pool {} marked as inactive due to errors", pool.getAddress());
        poolRepository.save(pool);
    }

    /**
     * Извлекает адрес пула из транзакции
     */
    private String extractPoolAddress(RaydiumSwapTransaction swapTx) {
        if (swapTx.getInstructions() == null) {
            log.debug("No instructions in transaction {}", swapTx.getSignature());
            return null;
        }

        Optional<InstructionData> raydiumInstruction = swapTx.getInstructions().stream()
                .filter(instruction -> RAYDIUM_PROGRAM_ID.equals(instruction.getProgramId()))
                .findFirst();

        if (raydiumInstruction.isEmpty()) {
            log.debug("No Raydium instruction in transaction {}", swapTx.getSignature());
            return null;
        }

        InstructionData instruction = raydiumInstruction.get();
        if (instruction.getAccounts() == null || instruction.getAccounts().size() < 3) {
            log.debug("Invalid accounts in Raydium instruction");
            return null;
        }

        String poolAddress = instruction.getAccounts().get(2);
        log.info("Found pool address: {}", poolAddress);
        return poolAddress;
    }

    /**
     * Рассчитывает ожидаемое проскальзывание
     */
    public BigDecimal calculateExpectedSlippage(RaydiumPool pool, BigDecimal inputAmount, boolean isAtoB) {
        validateSlippageInput(pool, inputAmount);

        BigDecimal reserveIn = isAtoB ? pool.getLiquidityA() : pool.getLiquidityB();
        BigDecimal reserveOut = isAtoB ? pool.getLiquidityB() : pool.getLiquidityA();

        // Рассчитываем выход по формуле x * y = k
        BigDecimal k = reserveIn.multiply(reserveOut);
        BigDecimal newReserveIn = reserveIn.add(inputAmount);
        BigDecimal newReserveOut = k.divide(newReserveIn, 8, RoundingMode.HALF_UP);
        BigDecimal outputAmount = reserveOut.subtract(newReserveOut);

        return calculateSlippagePercentage(inputAmount, outputAmount, reserveIn, reserveOut);
    }

    /**
     * Проверяет входные данные для расчета проскальзывания
     */
    private void validateSlippageInput(RaydiumPool pool, BigDecimal inputAmount) {
        if (inputAmount == null || inputAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Input amount must be positive");
        }

        if (pool.getLiquidityA() == null || pool.getLiquidityB() == null ||
                pool.getLiquidityA().compareTo(BigDecimal.ZERO) <= 0 ||
                pool.getLiquidityB().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Invalid pool liquidity");
        }
    }

    /**
     * Рассчитывает процент проскальзывания
     */
    private BigDecimal calculateSlippagePercentage(
            BigDecimal inputAmount,
            BigDecimal outputAmount,
            BigDecimal reserveIn,
            BigDecimal reserveOut) {

        BigDecimal spotPrice = reserveOut.divide(reserveIn, 8, RoundingMode.HALF_UP);
        BigDecimal effectivePrice = outputAmount.divide(inputAmount, 8, RoundingMode.HALF_UP);

        return BigDecimal.ONE
                .subtract(effectivePrice.divide(spotPrice, 8,
                        RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100))
                .abs();
    }

    /**
     * Возвращает информацию о пуле по его адресу
     */
    public Optional<RaydiumPool> getPoolByAddress(String address) {
        return poolRepository.findById(address);
    }

    /**
     * Возвращает все активные пулы
     */
    public List<RaydiumPool> getAllActivePools() {
        return poolRepository.findByActiveTrue();
    }

    /**
     * Деактивирует пул
     */
    @Transactional
    public void deactivatePool(String poolAddress) {
        poolRepository.findById(poolAddress).ifPresent(pool -> {
            pool.setActive(false);
            pool.setLastUpdate(LocalDateTime.now());
            poolRepository.save(pool);
            log.info("Pool {} has been deactivated", poolAddress);
        });
    }

    /**
     * Проверяет активность пула
     */
    public boolean isPoolActive(String poolAddress) {
        return poolRepository.findById(poolAddress)
                .map(RaydiumPool::isActive)
                .orElse(false);
    }
}