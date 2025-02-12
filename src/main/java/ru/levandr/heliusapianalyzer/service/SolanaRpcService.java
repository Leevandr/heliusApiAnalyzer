package ru.levandr.heliusapianalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolanaRpcService {
    private final WebClient webClient;

    @Value("${app.helius.rpc-endpoint}")
    private String rpcEndpoint;

    @Value("${app.helius.api-key}")
    private String apiKey;

    /**
     * Получает данные аккаунта пула из блокчейна
     *
     * @param poolAddress адрес пула
     * @return данные аккаунта или null при ошибке
     */
    public byte[] getPoolAccountData(String poolAddress) {
        try {
            // Формируем RPC запрос
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "getAccountInfo",
                    "params", List.of(poolAddress, Map.of("encoding", "base64"))
            );

            // Отправляем запрос
            String url = String.format("%s/?api-key=%s", rpcEndpoint, apiKey);

            return webClient.post()
                    .uri(url)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(response -> {
                        String data = response.at("/result/value/data/0").asText();
                        return Base64.getDecoder().decode(data);
                    })
                    .block();

        } catch (Exception e) {
            log.error("Error getting pool account data for {}: {}",
                    poolAddress, e.getMessage());
            return null;
        }
    }
}
