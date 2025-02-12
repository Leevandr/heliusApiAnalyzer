package ru.levandr.heliusapianalyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.levandr.heliusapianalyzer.model.entity.RaydiumPool;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Репозиторий для работы с пулами Raydium
 * Предоставляет методы для сохранения и получения информации о пулах
 */
public interface RaydiumPoolRepository extends JpaRepository<RaydiumPool, String> {
    // Получить все активные пулы
    List<RaydiumPool> findByActiveTrue();

    // Получить пулы, обновленные после определенного времени
    @Query("SELECT p FROM RaydiumPool p WHERE p.active = true AND p.lastUpdate > :threshold")
    List<RaydiumPool> findRecentActivePools(LocalDateTime threshold);

    // Получить пулы по токену
    List<RaydiumPool> findByTokenAMintOrTokenBMint(String tokenMint, String tokenMint2);
}
