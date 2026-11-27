package com.videostream.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração MVC do adaptador web.
 *
 * <p>Para players em navegador é essencial expor {@code Content-Range},
 * {@code Accept-Ranges} e {@code ETag}: sem isso o JavaScript não consegue
 * controlar o buffer e o seek dinâmico.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfiguration implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public WebConfiguration(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods(
                        HttpMethod.GET.name(),
                        HttpMethod.HEAD.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.OPTIONS.name())
                .allowedHeaders("*")
                .exposedHeaders(
                        HttpHeaders.ACCEPT_RANGES,
                        HttpHeaders.CONTENT_RANGE,
                        HttpHeaders.CONTENT_LENGTH,
                        HttpHeaders.CONTENT_DISPOSITION,
                        HttpHeaders.ETAG,
                        HttpHeaders.LAST_MODIFIED,
                        HttpHeaders.LOCATION,
                        "X-Request-Id")
                .maxAge(corsProperties.maxAge().toSeconds());
    }
}
