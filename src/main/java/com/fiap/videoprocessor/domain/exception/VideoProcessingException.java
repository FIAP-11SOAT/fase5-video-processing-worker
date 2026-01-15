package com.fiap.videoprocessor.domain.exception;

/**
 * Exceção lançada quando há erro no processamento do vídeo
 */
public class VideoProcessingException extends DomainException {
    public VideoProcessingException(String message) {
        super(message);
    }
    
    public VideoProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
