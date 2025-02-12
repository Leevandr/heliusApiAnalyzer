package ru.levandr.heliusapianalyzer.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.helius")
public class HeliusProperties {
    private String apiKey;
    private String rpcEndpoint;
    private String wsEndpoint;
}
