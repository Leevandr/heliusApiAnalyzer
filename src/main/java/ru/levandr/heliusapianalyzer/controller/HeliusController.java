package ru.levandr.heliusapianalyzer.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.levandr.heliusapianalyzer.service.HeliusService;

/**
 * Контроллер для ручного управления анализом Raydium транзакций
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/helius")
@RequiredArgsConstructor
public class HeliusController {

    private final HeliusService heliusService;

    /**
     * Запускает анализ последних транзакций Raydium
     * @return результат операции
     */
    @PostMapping("/analyze")
    public ResponseEntity<String> startAnalysis() {
        try {
            log.info("Starting manual analysis of Raydium transactions");
            heliusService.processRaydiumTransactions();
            return ResponseEntity.ok("Analysis started successfully");
        } catch (Exception e) {
            log.error("Error starting analysis: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Error starting analysis: " + e.getMessage());
        }
    }
}