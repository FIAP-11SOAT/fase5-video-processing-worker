package com.fiap.videoprocessor.infrastructure.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.domain.model.Frame;
import com.fiap.videoprocessor.domain.model.ProcessingResult;
import com.fiap.videoprocessor.domain.ports.input.ProcessVideoUseCase;
import com.fiap.videoprocessor.infrastructure.adapter.rest.VideoProcessingController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VideoProcessingControllerTest {

    @Mock
    private ProcessVideoUseCase processVideoUseCase;

    @InjectMocks
    private VideoProcessingController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new com.fiap.videoprocessor.infrastructure.adapter.rest.GlobalExceptionHandler())
                .build();
    }

    @Test
    void health_deveRetornarUp() throws Exception {
        mockMvc.perform(get("/api/videos/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("Video Processing Worker"));
    }

    @Test
    void processVideo_deveRetornarSucesso() throws Exception {
        ProcessingResult result = ProcessingResult.builder()
                .videoId("vid123")
                .success(true)
                .message("Processamento concluído! 3 frames extraídos.")
                .frameCount(3)
                .frames(List.of(Frame.builder().fileName("f1.png").frameNumber(1).sizeInBytes(512).build()))
                .zipS3Key("user/vid123.zip")
                .processingTimeMs(1200L)
                .build();

        when(processVideoUseCase.process(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(result);

        String requestBody = """
                {
                  "videoId": "vid123",
                  "s3Key": "user/vid123.mp4",
                  "inputBucket": "fiap-videos",
                  "outputBucket": "fiap-output"
                }
                """;

        mockMvc.perform(post("/api/videos/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoId").value("vid123"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.frameCount").value(3))
                .andExpect(jsonPath("$.zipS3Key").value("user/vid123.zip"));
    }

    @Test
    void processVideo_deveRetornar500QuandoUseCase_lancaExcecao() throws Exception {
        when(processVideoUseCase.process(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new VideoProcessingException("Falha inesperada"));

        String requestBody = """
                {
                  "videoId": "vid123",
                  "s3Key": "user/vid123.mp4",
                  "inputBucket": "fiap-videos"
                }
                """;

        mockMvc.perform(post("/api/videos/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }
}
