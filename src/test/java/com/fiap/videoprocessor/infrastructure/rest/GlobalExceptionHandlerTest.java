package com.fiap.videoprocessor.infrastructure.rest;

import com.fiap.videoprocessor.domain.exception.DomainException;
import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.infrastructure.adapter.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleDomainException_deveRetornar400() {
        DomainException ex = new DomainException("regra de negocio violada");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleDomainException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("regra de negocio violada");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void handleDomainException_deveAceitarVideoProcessingException() {
        VideoProcessingException ex = new VideoProcessingException("falha no processamento");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleDomainException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("falha no processamento");
    }

    @Test
    void handleGenericException_deveRetornar500() {
        Exception ex = new RuntimeException("erro inesperado");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Erro interno do servidor");
    }

    @Test
    void handleValidationException_deveRetornar400ComErrosDeCampo() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "objectName");
        bindingResult.addError(new FieldError("objectName", "videoId", "não pode ser nulo"));
        bindingResult.addError(new FieldError("objectName", "s3Key", "não pode ser vazio"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).contains("Erro de validação");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void errorResponse_deveCriarRegistroComCamposCorretos() {
        java.time.Instant now = java.time.Instant.now();
        GlobalExceptionHandler.ErrorResponse errorResponse =
                new GlobalExceptionHandler.ErrorResponse(400, "mensagem", now);

        assertThat(errorResponse.status()).isEqualTo(400);
        assertThat(errorResponse.message()).isEqualTo("mensagem");
        assertThat(errorResponse.timestamp()).isEqualTo(now);
    }
}
