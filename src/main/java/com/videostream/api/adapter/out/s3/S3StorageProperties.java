package com.videostream.api.adapter.out.s3;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.Optional;

/**
 * Configuração do adaptador S3.
 *
 * <p>As credenciais são opcionais: quando ausentes, vale a cadeia padrão do AWS SDK
 * (variáveis de ambiente, profile, IAM Role/IRSA). Informe {@code access-key-id} e
 * {@code secret-access-key} apenas para desenvolvimento local (LocalStack/MinIO).
 */
@Validated
@ConfigurationProperties(prefix = "video.storage")
public record S3StorageProperties(
        @NotBlank String bucket,
        @NotBlank String region,
        String keyPrefix,
        String endpointOverride,
        boolean pathStyleAccess,
        boolean chunkedEncodingEnabled,
        String accessKeyId,
        String secretAccessKey) {

    public S3StorageProperties {
        keyPrefix = normalizePrefix(keyPrefix);
        if ((accessKeyId == null || accessKeyId.isBlank()) != (secretAccessKey == null || secretAccessKey.isBlank())) {
            throw new IllegalArgumentException(
                    "video.storage.access-key-id e video.storage.secret-access-key devem ser informados juntos");
        }
    }

    /**
     * Endpoint alternativo (LocalStack, MinIO). Vazio em produção.
     */
    public Optional<URI> endpoint() {
        return Optional.ofNullable(endpointOverride)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(URI::create);
    }

    public boolean hasStaticCredentials() {
        return accessKeyId != null && !accessKeyId.isBlank();
    }

    private static String normalizePrefix(String rawPrefix) {
        String prefix = rawPrefix == null ? "" : rawPrefix.trim();
        if (prefix.isEmpty() || prefix.endsWith("/")) {
            return prefix;
        }
        return prefix + "/";
    }
}
