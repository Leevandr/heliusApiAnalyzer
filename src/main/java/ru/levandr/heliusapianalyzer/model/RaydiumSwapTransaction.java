package ru.levandr.heliusapianalyzer.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Базовая модель для парсинга SWAP транзакций из Helius API
 * Содержит только необходимые поля для анализа свопов
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RaydiumSwapTransaction {
    // Уникальный идентификатор транзакции
    private String signature;

    // Тип транзакции (SWAP)
    private String type;

    // Временная метка транзакции
    private Long timestamp;

    // Описание транзакции в человекочитаемом формате
    private String description;

    // Комиссия транзакции в lamports
    private Long fee;

    // Информация о перемещении токенов
    private List<TokenTransfer> tokenTransfers;

    // Инструкции транзакции (необходимы для анализа пула и параметров свопа)
    private List<InstructionData> instructions;
}