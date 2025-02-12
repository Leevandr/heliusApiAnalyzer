package ru.levandr.heliusapianalyzer.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.levandr.heliusapianalyzer.repository.RaydiumPoolRepository;
import ru.levandr.heliusapianalyzer.model.RaydiumSwapTransaction;
import ru.levandr.heliusapianalyzer.model.TokenTransfer;
import ru.levandr.heliusapianalyzer.model.entity.RaydiumPool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для работы с пулами Raydium
 * Отвечает за обработку и сохранение информации о пулах
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaydiumPoolService {
    private static final String RAYDIUM_PROGRAM_ID = "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8";
    private final RaydiumPoolRepository poolRepository;
    private final SolanaRpcService solanaRpcService;


    /**
     * Обрабатывает информацию о пуле из транзакции свопа
     *
     * @param swapTx транзакция свопа
     */
    @Transactional
    public void processPoolFromSwap(RaydiumSwapTransaction swapTx) {
        try {
            String poolAddress = extractPoolAddress(swapTx);
            if (poolAddress == null) {
                log.warn("Could not extract pool address from transaction {}",
                        swapTx.getSignature());
                return;
            }

            RaydiumPool pool = poolRepository.findById(poolAddress)
                    .orElseGet(() -> createNewPool(poolAddress));

            // Обновляем информацию о токенах
            updatePoolTokens(pool, swapTx);

            // Обновляем ликвидность
            updatePoolLiquidity(pool);

            // Обновляем объем
            updateVolume24h(pool, swapTx);

            poolRepository.save(pool);
            log.info("Successfully processed pool {}", poolAddress);

        } catch (Exception e) {
            log.error("Error processing pool from swap tx {}: {}",
                    swapTx.getSignature(), e.getMessage());
        }
    }

    /**
     * Извлекает адрес пула из транзакции свопа Raydium
     * @param swapTx транзакция свопа
     * @return адрес пула или null если не найден
     */
    private String extractPoolAddress(RaydiumSwapTransaction swapTx) {
        if (swapTx.getInstructions() == null) {
            return null;
        }

        // Ищем инструкцию Raydium программы
        return swapTx.getInstructions().stream()
                .filter(instruction -> RAYDIUM_PROGRAM_ID.equals(instruction.getProgramId()))
                .filter(instruction -> instruction.getAccounts() != null &&
                        instruction.getAccounts().size() >= 3) // У Raydium свопов минимум 3 аккаунта
                .findFirst()
                .map(instruction -> {
                    // В Raydium свопах адрес пула обычно третий аккаунт
                    String potentialPoolAddress = instruction.getAccounts().get(2);
                    log.debug("Found potential pool address: {}", potentialPoolAddress);
                    return potentialPoolAddress;
                })
                .orElse(null);
    }

    /**
     * Создает новый пул с базовой информацией
     */
    private RaydiumPool createNewPool(String poolAddress) {
        RaydiumPool pool = new RaydiumPool();
        pool.setAddress(poolAddress);
        pool.setActive(true);
        pool.setLastUpdate(LocalDateTime.now());
        return pool;
    }


    /**
     * Устанавливает адреса токенов пула из транзакции
     */
    private void setPoolTokens(RaydiumPool pool, RaydiumSwapTransaction swapTx) {
        if (swapTx.getTokenTransfers() == null || swapTx.getTokenTransfers().size() < 2) {
            return;
        }

        List<TokenTransfer> transfers = swapTx.getTokenTransfers();
        pool.setTokenAMint(transfers.get(0).getMint());
        pool.setTokenBMint(transfers.get(1).getMint());
    }

    /**
     * Обновляет цену и объем на основе данных свопа
     */
    private void updatePriceAndVolume(RaydiumPool pool, RaydiumSwapTransaction swapTx) {
        if (swapTx.getTokenTransfers() == null || swapTx.getTokenTransfers().size() < 2) {
            return;
        }

        // Получаем данные о трансферах
        TokenTransfer inTransfer = swapTx.getTokenTransfers().get(0);
        TokenTransfer outTransfer = swapTx.getTokenTransfers().get(1);

        // Рассчитываем цену если оба трансфера валидны
        if (inTransfer.getTokenAmount() > 0 && outTransfer.getTokenAmount() > 0) {
            pool.setPrice(BigDecimal.valueOf(outTransfer.getTokenAmount() /
                    inTransfer.getTokenAmount()));
        }

        // Обновляем объем напрямую через метод updateVolume24h
        updateVolume24h(pool, swapTx);
    }

    /**
     * Обновляет объем торгов за 24 часа
     */
    private void updateVolume24h(RaydiumPool pool, RaydiumSwapTransaction swapTx) {
        if (swapTx.getTokenTransfers() == null || swapTx.getTokenTransfers().isEmpty()) {
            return;
        }

        // Берем объем из первого трансфера
        TokenTransfer transfer = swapTx.getTokenTransfers().get(0);
        BigDecimal amount = BigDecimal.valueOf(transfer.getTokenAmount());

        // Обновляем общий объем
        BigDecimal currentVolume = pool.getVolume24h() != null ?
                pool.getVolume24h() : BigDecimal.ZERO;
        pool.setVolume24h(currentVolume.add(amount));

        log.debug("Updated volume for pool {}: +{} = {}",
                pool.getAddress(), amount, pool.getVolume24h());
    }

    /**
     * Обновляет информацию о токенах пула
     */
    private void updatePoolTokens(RaydiumPool pool, RaydiumSwapTransaction swapTx) {
        if (swapTx.getTokenTransfers() == null || swapTx.getTokenTransfers().size() < 2) {
            return;
        }

        // Если токены еще не установлены
        if (pool.getTokenAMint() == null || pool.getTokenBMint() == null) {
            TokenTransfer firstTransfer = swapTx.getTokenTransfers().get(0);
            TokenTransfer secondTransfer = swapTx.getTokenTransfers().get(1);

            pool.setTokenAMint(firstTransfer.getMint());
            pool.setTokenBMint(secondTransfer.getMint());

            log.info("Set tokens for pool {}: A={}, B={}",
                    pool.getAddress(), firstTransfer.getMint(), secondTransfer.getMint());
        }
    }

    /**
     * Обновляет данные о ликвидности пула
     */
    private void updatePoolLiquidity(RaydiumPool pool) {
        try {
            byte[] accountData = solanaRpcService.getPoolAccountData(pool.getAddress());
            if (accountData == null || accountData.length < 192) { // Validate minimum data length
                log.warn("Invalid pool account data length for {}: expected >= 192, got {}",
                        pool.getAddress(), accountData != null ? accountData.length : 0);
                return;
            }

            ByteBuffer buffer = ByteBuffer.wrap(accountData);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Skip authority (32 bytes) and status (1 byte)
            buffer.position(33);

            // Read token mints (32 bytes each)
            byte[] tokenAMint = new byte[32];
            byte[] tokenBMint = new byte[32];
            buffer.get(tokenAMint);
            buffer.get(tokenBMint);

            // Read reserves (8 bytes each)
            BigDecimal reserveA = BigDecimal.valueOf(buffer.getLong());
            BigDecimal reserveB = BigDecimal.valueOf(buffer.getLong());

            // Update pool data
            pool.setLiquidityA(reserveA);
            pool.setLiquidityB(reserveB);

            // Calculate price with proper decimal handling
            if (reserveB.compareTo(BigDecimal.ZERO) > 0) {
                pool.setPrice(reserveA.divide(reserveB, 8, RoundingMode.HALF_UP));
            }

            // Update last update timestamp
            pool.setLastUpdate(LocalDateTime.now());

            log.info("Updated liquidity for pool {}: A={}, B={}, price={}",
                    pool.getAddress(), reserveA, reserveB, pool.getPrice());

        } catch (Exception e) {
            log.error("Error updating pool liquidity for {}: {}",
                    pool.getAddress(), e.getMessage(), e);
            // Consider marking pool as inactive if errors persist
            handlePoolUpdateError(pool);
        }
    }

    private void handlePoolUpdateError(RaydiumPool pool) {
        // Implement error tracking logic
        // If multiple consecutive errors, consider marking pool as inactive
        pool.setActive(false);
        log.warn("Pool {} marked as inactive due to persistent errors", pool.getAddress());
    }

}
