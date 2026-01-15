package com.fiap.videoprocessor.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Representa um frame extraído do vídeo
 */
@Getter
@Builder
public class Frame {
    private final String fileName;
    private final int frameNumber;
    private final byte[] data;
    private final long sizeInBytes;
    private final String s3Key;
}
