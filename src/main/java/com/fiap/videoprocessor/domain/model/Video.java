package com.fiap.videoprocessor.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.Instant;

/**
 * Entidade de domínio que representa um vídeo a ser processado
 */
@Getter
@Builder
@With
public class Video {
    private final String id;
    private final String s3Key;
    private final String bucket;
    private final String fileName;
    private final ProcessingStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    
    public enum ProcessingStatus {
        PENDING,
        DOWNLOADING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
    
    public Video markAsDownloading() {
        return this.withStatus(ProcessingStatus.DOWNLOADING)
                   .withUpdatedAt(Instant.now());
    }
    
    public Video markAsProcessing() {
        return this.withStatus(ProcessingStatus.PROCESSING)
                   .withUpdatedAt(Instant.now());
    }
    
    public Video markAsCompleted() {
        return this.withStatus(ProcessingStatus.COMPLETED)
                   .withUpdatedAt(Instant.now());
    }
    
    public Video markAsFailed() {
        return this.withStatus(ProcessingStatus.FAILED)
                   .withUpdatedAt(Instant.now());
    }
}
