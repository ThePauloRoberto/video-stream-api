package com.videostream.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuração do OpenAPI 3 / Swagger UI.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI videoStreamOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Video Stream API")
                        .version("1.0.0")
                        .description("""
                                API de Upload e Streaming de vídeo sobre AWS S3 / LocalStack com Arquitetura Hexagonal, \
                                Clean Architecture e suporte a HTTP Range Requests (entrega em chunks de 1 MB).
                                
                                ### Como testar pelo Swagger UI:
                                1. **Upload**: Expanda `POST /api/videos/upload`, clique em **Try it out**, selecione um arquivo `.mp4` em `file` e clique em **Execute**.
                                2. **Catálogo**: Expanda `GET /api/v1/videos` para listar os vídeos salvos no bucket.
                                3. **Metadados**: Expanda `GET /api/v1/videos/{videoId}` informando o `id` retornado no upload (ex.: `demo.mp4`).
                                4. **Streaming / Range Request**: Expanda `GET /api/v1/videos/{videoId}/stream` e informe o cabeçalho `Range` (ex.: `bytes=0-` para receber o 1º chunk de 1 MB com status `206 Partial Content`).""")
                        .contact(new Contact().name("Video Stream API")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Servidor Local")));
    }
}
