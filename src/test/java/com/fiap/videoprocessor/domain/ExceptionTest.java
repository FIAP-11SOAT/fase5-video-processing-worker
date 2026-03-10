package com.fiap.videoprocessor.domain;

import com.fiap.videoprocessor.domain.exception.DomainException;
import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTest {

    @Test
    void domainException_deveCriarComMensagem() {
        DomainException ex = new DomainException("Erro de dominio");
        assertThat(ex.getMessage()).isEqualTo("Erro de dominio");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void domainException_deveCriarComMensagemECausa() {
        RuntimeException cause = new RuntimeException("causa raiz");
        DomainException ex = new DomainException("Erro de dominio", cause);
        assertThat(ex.getMessage()).isEqualTo("Erro de dominio");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void videoProcessingException_deveEstenderDomainException() {
        VideoProcessingException ex = new VideoProcessingException("Erro de processamento");
        assertThat(ex).isInstanceOf(DomainException.class);
        assertThat(ex.getMessage()).isEqualTo("Erro de processamento");
    }

    @Test
    void videoProcessingException_deveCriarComCausa() {
        RuntimeException cause = new RuntimeException("falha ffmpeg");
        VideoProcessingException ex = new VideoProcessingException("Falha ao extrair frames", cause);
        assertThat(ex.getMessage()).isEqualTo("Falha ao extrair frames");
        assertThat(ex.getCause()).isEqualTo(cause);
    }
}
