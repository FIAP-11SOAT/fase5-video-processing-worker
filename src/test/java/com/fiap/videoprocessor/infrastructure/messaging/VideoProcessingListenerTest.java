package com.fiap.videoprocessor.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.domain.model.VideoDynamoModel;
import com.fiap.videoprocessor.domain.ports.input.ProcessVideoUseCase;
import com.fiap.videoprocessor.domain.ports.output.VideoStatusPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VideoProcessingListenerTest {

    @Mock
    private ProcessVideoUseCase processVideoUseCase;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private VideoStatusPort videoStatusPort;

    @InjectMocks
    private VideoProcessingListener listener;

    private static final String VALID_S3_EVENT = """
            {
              "Records": [{
                "s3": {
                  "bucket": {"name": "my-bucket"},
                  "object": {"key": "user123/video456.mp4", "size": 1024}
                }
              }]
            }
            """;

    @Test
    void processMessage_deveProcessarComSucesso_quandoVideoEncontradoNoDynamo() throws Exception {
        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage s3Event =
                buildS3Event("my-bucket", "user123/video456.mp4", 1024L);

        VideoDynamoModel videoModel = VideoDynamoModel.builder()
                .id("video456.mp4")
                .userId("real-user-uuid")
                .name("meu-video.mp4")
                .build();

        when(objectMapper.readValue(anyString(),
                eq(com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.class)))
                .thenReturn(s3Event);
        when(videoStatusPort.findByVideoKey("user123/video456.mp4"))
                .thenReturn(Optional.of(videoModel));

        listener.processMessage(VALID_S3_EVENT);

        verify(processVideoUseCase).processVideo(any());
    }

    @Test
    void processMessage_deveProcessarComSucesso_quandoVideoNaoEncontradoNoDynamo() throws Exception {
        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage s3Event =
                buildS3Event("my-bucket", "user123/video456.mp4", 1024L);

        when(objectMapper.readValue(anyString(),
                eq(com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.class)))
                .thenReturn(s3Event);
        when(videoStatusPort.findByVideoKey("user123/video456.mp4"))
                .thenReturn(Optional.empty());

        listener.processMessage(VALID_S3_EVENT);

        verify(processVideoUseCase).processVideo(any());
    }

    @Test
    void processMessage_deveProcessarComFallback_quandoDynamoLancaExcecaoGenerica() throws Exception {
        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage s3Event =
                buildS3Event("my-bucket", "user123/video456.mp4", 1024L);

        when(objectMapper.readValue(anyString(),
                eq(com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.class)))
                .thenReturn(s3Event);
        when(videoStatusPort.findByVideoKey(anyString()))
                .thenThrow(new RuntimeException("DynamoDB connection error"));

        // Generic RuntimeException from DynamoDB lookup is swallowed; processing continues
        listener.processMessage(VALID_S3_EVENT);

        verify(processVideoUseCase).processVideo(any());
    }

    @Test
    void processMessage_deveLancarExcecao_quandoDynamoLancaVideoProcessingException() throws Exception {
        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage s3Event =
                buildS3Event("my-bucket", "user123/video456.mp4", 1024L);

        when(objectMapper.readValue(anyString(),
                eq(com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.class)))
                .thenReturn(s3Event);
        when(videoStatusPort.findByVideoKey(anyString()))
                .thenThrow(new VideoProcessingException("DynamoDB error"));

        assertThatThrownBy(() -> listener.processMessage(VALID_S3_EVENT))
                .isInstanceOf(VideoProcessingException.class);
    }

    @Test
    void processMessage_deveLancarExcecao_quandoJsonInvalido() throws Exception {
        when(objectMapper.readValue(anyString(),
                eq(com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.class)))
                .thenThrow(mock(JsonProcessingException.class));

        assertThatThrownBy(() -> listener.processMessage("invalid json"))
                .isInstanceOf(VideoProcessingException.class);
    }

    @Test
    void processMessage_deveIgnorar_quandoRecordsVazio() throws Exception {
        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage s3Event =
                new com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage();
        s3Event.setRecords(java.util.List.of());

        when(objectMapper.readValue(anyString(),
                eq(com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.class)))
                .thenReturn(s3Event);

        listener.processMessage("{}");

        verify(processVideoUseCase, never()).processVideo(any());
    }

    @Test
    void processMessage_deveIgnorar_quandoRecordsNull() throws Exception {
        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage s3Event =
                new com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage();

        when(objectMapper.readValue(anyString(),
                eq(com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.class)))
                .thenReturn(s3Event);

        listener.processMessage("{}");

        verify(processVideoUseCase, never()).processVideo(any());
    }

    @Test
    void processMessage_deveLancarExcecao_quandoKeyFormatoInvalido() throws Exception {
        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage s3Event =
                buildS3Event("my-bucket", "invalid-key", 512L);

        when(objectMapper.readValue(anyString(),
                eq(com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.class)))
                .thenReturn(s3Event);

        assertThatThrownBy(() -> listener.processMessage(VALID_S3_EVENT))
                .isInstanceOf(VideoProcessingException.class);
    }

    @Test
    void processMessage_deveLancarExcecao_quandoVideoNomeSemExtensao() throws Exception {
        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage s3Event =
                buildS3Event("my-bucket", "user123/video456.mp4", 1024L);

        when(objectMapper.readValue(anyString(),
                eq(com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.class)))
                .thenReturn(s3Event);
        when(videoStatusPort.findByVideoKey(anyString())).thenReturn(Optional.empty());
        doThrow(new VideoProcessingException("processing failed"))
                .when(processVideoUseCase).processVideo(any());

        assertThatThrownBy(() -> listener.processMessage(VALID_S3_EVENT))
                .isInstanceOf(VideoProcessingException.class);
    }

    @Test
    void processMessage_deveUsarNomePadrao_quandoVideoDynamoNomNull() throws Exception {
        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage s3Event =
                buildS3Event("my-bucket", "user123/video456.mp4", 1024L);

        VideoDynamoModel videoModel = VideoDynamoModel.builder()
                .id("video456.mp4")
                .userId("real-user-uuid")
                .name(null) // name is null
                .build();

        when(objectMapper.readValue(anyString(),
                eq(com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.class)))
                .thenReturn(s3Event);
        when(videoStatusPort.findByVideoKey(anyString())).thenReturn(Optional.of(videoModel));

        listener.processMessage(VALID_S3_EVENT);

        verify(processVideoUseCase).processVideo(
                argThat(msg -> "video456.mp4".equals(msg.getVideoName())));
    }

    // Helper
    private com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage buildS3Event(
            String bucket, String key, Long size) {
        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.S3Bucket s3Bucket =
                new com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.S3Bucket();
        s3Bucket.setName(bucket);

        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.S3Object s3Object =
                new com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.S3Object();
        s3Object.setKey(key);
        s3Object.setSize(size);

        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.S3Data s3Data =
                new com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.S3Data();
        s3Data.setBucket(s3Bucket);
        s3Data.setObject(s3Object);

        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.S3EventRecord record =
                new com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage.S3EventRecord();
        record.setS3(s3Data);

        com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage event =
                new com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage();
        event.setRecords(java.util.List.of(record));
        return event;
    }
}
