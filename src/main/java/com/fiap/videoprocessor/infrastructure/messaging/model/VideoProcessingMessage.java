package com.fiap.videoprocessor.infrastructure.messaging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dados extraídos do evento S3 para processamento
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoProcessingMessage {
    private String bucket;
    private String key;
    private String userId;
    private String videoId;
    private Long size;
}
