package ru.levandr.heliusapianalyzer.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.levandr.heliusapianalyzer.service.analyzer.HeliusAnalyzer;

@RestController
@RequestMapping("/api/analyze")
@RequiredArgsConstructor
public class AnalyzerController {

    private final HeliusAnalyzer heliusAnalyzer;

    @PostMapping("/raydium")
    public void analyzeRaydium() {
        heliusAnalyzer.analyzeRaydiumTransactions();
    }
}
