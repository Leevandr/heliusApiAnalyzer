package ru.levandr.heliusapianalyzer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.levandr.heliusapianalyzer.model.entity.RaydiumPool;
import ru.levandr.heliusapianalyzer.service.RaydiumPoolService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pools")
@RequiredArgsConstructor
public class PoolController {
    private final RaydiumPoolService poolService;

    @GetMapping("/{address}")
    public ResponseEntity<RaydiumPool> getPool(@PathVariable String address) {
        return poolService.getPoolByAddress(address)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    public List<RaydiumPool> getActivePools() {
        return poolService.getAllActivePools();
    }

    @PostMapping("/{address}/deactivate")
    public ResponseEntity<Void> deactivatePool(@PathVariable String address) {
        if (poolService.isPoolActive(address)) {
            poolService.deactivatePool(address);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{address}/status")
    public ResponseEntity<Map<String, Boolean>> getPoolStatus(@PathVariable String address) {
        boolean isActive = poolService.isPoolActive(address);
        return ResponseEntity.ok(Map.of("active", isActive));
    }
}
