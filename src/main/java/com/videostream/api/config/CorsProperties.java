package com.videostream.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * CORS para players web. Em produção com CloudFront/API Gateway, normalmente esta
 * responsabilidade fica no CDN; aqui mantemos configurável para uso direto da API.
 */
@ConfigurationProperties(prefix = "video.cors")
public record CorsProperties(List<String> allowedOrigins, Duration maxAge) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null || allowedOrigins.isEmpty()
                ? List.of("*")
                : List.copyOf(allowedOrigins);
        maxAge = maxAge == null ? Duration.ofHours(1) : maxAge;
    }
}
