package com.fiap.videoprocessor.domain.exception;

/**
 * Exceção de domínio base
 */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
    
    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
