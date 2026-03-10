package com.fiap.videoprocessor.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum de status do vídeo
 */
@Getter
@RequiredArgsConstructor
public enum StatusEnum {
    UPLOADED("uploaded", "Frameify: Seu vídeo foi carregado \uD83E\uDD29"),
    PROCESSED("processed", "Frameify: Seus frames estão disponíveis ! \uD83D\uDE80"),
    ERROR_PROCESSING("error-processing", "Frameify: Houve um problema ao processar seu vídeo \uD83E\uDD26\uD83C\uDFFD");

    private final String value;
    private final String description;
}
