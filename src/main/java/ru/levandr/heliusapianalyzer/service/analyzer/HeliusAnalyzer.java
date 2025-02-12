package ru.levandr.heliusapianalyzer.service.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeliusAnalyzer {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${app.helius.api-key}")
    private String apiKey;

    private static final String RAYDIUM_ADDRESS = "675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8";
    private static final String API_BASE_URL = "https://api.helius.xyz/v0";

    public void analyzeRaydiumTransactions() {
        String url = String.format("%s/addresses/%s/transactions/?api-key=%s",
                API_BASE_URL, RAYDIUM_ADDRESS, apiKey);

        webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(response -> {
                    try {
                        // Сохраняем сырой ответ
                        saveToFile(response);

                        // Логируем структуру
                        Object json = objectMapper.readTree(response);
                        log.info("Response structure: {}",
                                objectMapper.writerWithDefaultPrettyPrinter()
                                        .writeValueAsString(json));

                    } catch (Exception e) {
                        log.error("Error processing response", e);
                    }
                })
                .subscribe();
    }

    private void saveToFile(String content) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path file = Path.of("analysis", timestamp + "_" + "raw_response.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Saved response to {}", file);
        } catch (Exception e) {
            log.error("Error saving to file", e);
        }
    }
}