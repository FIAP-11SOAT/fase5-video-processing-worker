package com.fiap.videoprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicação principal - Video Processing Worker
 * 
 * Worker para processar vídeos armazenados no S3, extraindo frames
 * e salvando o resultado de volta no S3.
 * 
 * Arquitetura Hexagonal:
 * - domain: Entidades e regras de negócio puras
 * - application: Casos de uso e serviços de aplicação
 * - infrastructure: Adapters e configurações (S3, FFmpeg, REST, etc)
 */
@SpringBootApplication
public class VideoProcessorApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(VideoProcessorApplication.class, args);
    }
}
