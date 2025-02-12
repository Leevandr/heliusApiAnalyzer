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
            log.info("Fetching data for pool: {}", poolAddress);

            // Добавляем commitment: "confirmed" для получения подтвержденных данных
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", "getAccountInfo",
                    "params", List.of(
                            poolAddress,
                            Map.of(
                                    "encoding", "base64",
                                    "commitment", "confirmed",
                                    "dataSlice", Map.of(
                                            "offset", 0,
                                            "length", 300 // Увеличиваем размер получаемых данных
                                    )
                            )
                    )
            );

            String url = String.format("%s/?api-key=%s", rpcEndpoint, apiKey);

            log.info("Sending RPC request to: {}", url);
            log.debug("Request body: {}", request);

            JsonNode response = webClient.post()
                    .uri(url)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("result") || !response.get("result").has("value")) {
                log.error("Invalid response format for pool {}: {}", poolAddress, response);
                return null;
            }

            JsonNode value = response.get("result").get("value");
            if (!value.has("data") || value.get("data").size() < 1) {
                log.error("No data field in response for pool {}: {}", poolAddress, value);
                return null;
            }

            String data = value.get("data").get(0).asText();
            if (data.isEmpty()) {
                log.error("Empty data for pool {}", poolAddress);
                return null;
            }

            byte[] decoded = Base64.getDecoder().decode(data);
            log.info("Decoded data length for pool {}: {}", poolAddress, decoded.length);

            return decoded;

        } catch (Exception e) {
            log.error("Error getting pool account data for {}: {}",
                    poolAddress, e.getMessage(), e);
            return null;
        }
    }
}
