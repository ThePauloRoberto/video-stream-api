package com.videostream.api.adapter.out.s3;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Cria o {@link S3Client} (AWS SDK v2) com base nas propriedades da aplicação.
 *
 * <p>O cliente é thread-safe e deve ser um singleton; o {@code destroyMethod}
 * devolve os recursos do pool HTTP no shutdown.
 */
@Configuration(proxyBeanMethods = false)
public class S3ClientConfiguration {

    @Bean(destroyMethod = "close")
    S3Client s3Client(S3StorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                // MinIO/LocalStack não suportam virtual-hosted style em localhost
                .forcePathStyle(properties.pathStyleAccess())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .serviceConfiguration(S3Configuration.builder()
                        // Desabilita aws-chunked, que alguns S3-compatible não implementam
                        .chunkedEncodingEnabled(properties.chunkedEncodingEnabled())
                        .build());

        properties.endpoint().ifPresent(builder::endpointOverride);
        return builder.build();
    }

    private static AwsCredentialsProvider credentialsProvider(S3StorageProperties properties) {
        if (properties.hasStaticCredentials()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
