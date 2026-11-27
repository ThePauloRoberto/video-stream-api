package com.videostream.api.config;

import com.videostream.api.application.port.in.GetVideoMetadataUseCase;
import com.videostream.api.application.port.in.ListVideosUseCase;
import com.videostream.api.application.port.in.StreamVideoUseCase;
import com.videostream.api.application.port.in.UploadVideoUseCase;
import com.videostream.api.application.service.VideoService;
import com.videostream.api.application.service.VideoStreamingService;
import com.videostream.api.domain.port.VideoStoragePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Faz o wiring dos casos de uso.
 *
 * <p>A camada de aplicação não tem anotações de Spring; ela é instanciada aqui e
 * exposta ao adaptador web pelas interfaces (portas de entrada). O mesmo serviço
 * (stateless) atende às três portas.
 */
@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {

    public static final String SERVICE_BEAN_NAME = "videoStreamingService";

    @Bean(SERVICE_BEAN_NAME)
    VideoStreamingService videoStreamingService(VideoStoragePort videoStoragePort,
                                                VideoStreamingProperties properties) {
        return new VideoStreamingService(videoStoragePort, properties.maxRangeBytes());
    }

    @Bean
    StreamVideoUseCase streamVideoUseCase(
            @Qualifier(SERVICE_BEAN_NAME) VideoStreamingService service) {
        return service;
    }

    @Bean
    GetVideoMetadataUseCase getVideoMetadataUseCase(
            @Qualifier(SERVICE_BEAN_NAME) VideoStreamingService service) {
        return service;
    }

    @Bean
    ListVideosUseCase listVideosUseCase(
            @Qualifier(SERVICE_BEAN_NAME) VideoStreamingService service) {
        return service;
    }

    @Bean
    VideoService videoService(VideoStoragePort videoStoragePort) {
        return new VideoService(videoStoragePort);
    }

    @Bean
    UploadVideoUseCase uploadVideoUseCase(VideoService videoService) {
        return videoService;
    }
}
