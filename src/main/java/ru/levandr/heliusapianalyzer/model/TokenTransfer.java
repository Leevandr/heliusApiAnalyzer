package ru.levandr.heliusapianalyzer.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Модель для отслеживания перемещения токенов в рамках свопа
 * Позволяет определить входящие и исходящие токены, их количество и участников
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenTransfer {
    // Адрес токена
    private String mint;

    // Количество переведенных токенов
    private Double tokenAmount;

    // Аккаунт отправителя
    private String fromUserAccount;

    // Аккаунт получателя
    private String toUserAccount;

    // Количество десятичных знаков токена
    private Integer decimals;
}
