package ru.levandr.heliusapianalyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Модель для парсинга инструкций транзакции
 * Необходима для анализа параметров свопа и работы с пулами
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstructionData {
    // Список аккаунтов участвующих в инструкции
    private List<String> accounts;

    // Данные инструкции в формате base58
    private String data;

    // ID программы, которая выполняет инструкцию
    private String programId;
}
