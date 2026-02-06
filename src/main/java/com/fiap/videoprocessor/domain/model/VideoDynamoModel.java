package com.fiap.videoprocessor.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Modelo de domínio para vídeo no DynamoDB
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoDynamoModel {
    private String videoKey;
    private String id;
    private String userId;
    private String name;
    private String status; // processing, success, error
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String errorMessage;
    private String processedVideoKey; // Chave do arquivo processado no S3
}
