package com.videostream.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VideoStreamApiApplicationTests {

    @Test
    void contextLoads() {
        // garante que o wiring (portas, adaptadores, configurações) sobe sem S3 disponível
    }
}
