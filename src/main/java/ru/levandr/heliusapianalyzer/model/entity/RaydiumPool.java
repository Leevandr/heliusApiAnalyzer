package ru.levandr.heliusapianalyzer.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Сущность пула Raydium
 * Хранит основную информацию о торговом пуле и его состоянии
 */
@Entity
@Table(name = "raydium_pools")
@Data
public class RaydiumPool {
    @Id
    @Column(length = 44)
    private String address;      // Адрес пула в сети Solana

    @Column(length = 44)
    private String tokenAMint;   // Адрес первого токена

    @Column(length = 44)
    private String tokenBMint;   // Адрес второго токена

    @Column(precision = 24, scale = 8)
    private BigDecimal price;    // Текущая цена токена A в токенах B

    @Column(precision = 24, scale = 8)
    private BigDecimal liquidityA; // Ликвидность первого токена

    @Column(precision = 24, scale = 8)
    private BigDecimal liquidityB; // Ликвидность второго токена

    @Column(precision = 24, scale = 8)
    private BigDecimal volume24h;  // Объем торгов за 24 часа

    private LocalDateTime lastUpdate; // Время последнего обновления

    private boolean active = true;    // Активен ли пул
}