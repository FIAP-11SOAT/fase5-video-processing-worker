package com.fiap.videoprocessor.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Resultado do processamento de vídeo
 */
@Getter
@Builder
public class ProcessingResult {
    private final String videoId;
    private final boolean success;
    private final String message;
    private final int frameCount;
    private final List<Frame> frames;
    private final String zipS3Key;
    private final long processingTimeMs;
}
