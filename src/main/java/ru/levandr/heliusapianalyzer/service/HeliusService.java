package ru.levandr.heliusapianalyzer.service;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.levandr.heliusapianalyzer.model.RaydiumSwapTransaction;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeliusService {
    private final WebClient webClient;
    private final RaydiumPoolService poolService;

    @Value("${app.helius.api-key}")
    private String apiKey;

    @Value("${app.helius.api-base-url}")
    private String apiBaseUrl;

    /**
     * Получает и обрабатывает последние транзакции Raydium
     */
    public void processRaydiumTransactions() {
        String raydiumAddress = "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8";

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(7)
                .limitRefreshPeriod(Duration.ofSeconds(10))
                .timeoutDuration(Duration.ofMillis(500)) //Тайм-аут, если запрос занимает слишком много времени
                .build();

        RateLimiter rateLimiter = RateLimiterRegistry.of(config).rateLimiter("RaydiumRequests");


        webClient.get()
                .uri(buildTransactionHistoryUrl(raydiumAddress))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<RaydiumSwapTransaction>>() {})
                .flatMapIterable(transactions -> transactions)
                .filter(tx -> "SWAP".equals(tx.getType()))
                .publishOn(Schedulers.boundedElastic())
                .flatMap(tx -> Mono.fromCallable(() -> {
                    rateLimiter.acquirePermission();
                    log.info("Processing swap transaction: {}", tx.getSignature());
                    poolService.processPoolFromSwap(tx);
                    return tx;
                }))
                .doOnError(error ->
                        log.error("Error processing transactions: {}", error.getMessage(), error))
                .subscribe();
    }

    /**
     * Формирует URL для получения истории транзакций
     */
    private String buildTransactionHistoryUrl(String address) {
        return String.format("%s/v0/addresses/%s/transactions/?api-key=%s",
                apiBaseUrl, address, apiKey);
    }
}
