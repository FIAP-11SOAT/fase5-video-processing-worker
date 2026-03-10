package com.fiap.videoprocessor.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.videoprocessor.domain.model.StatusEnum;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SqsNotificationAdapterTest {

    @Mock
    private SqsTemplate sqsTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private SqsNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SqsNotificationAdapter(sqsTemplate, objectMapper);
        ReflectionTestUtils.setField(adapter, "notificationQueueName", "test-notification-queue");
    }

    @Test
    void sendNotification_deveExecutarSemExcecao_quandoSucesso() {
        assertThatCode(() ->
                adapter.sendNotification("user/video.mp4", "meu-video", "userId1", StatusEnum.PROCESSED))
                .doesNotThrowAnyException();
    }

    @Test
    void sendNotification_deveExecutarSemExcecao_quandoStatusError() {
        assertThatCode(() ->
                adapter.sendNotification("user/video.mp4", "meu-video", "userId1", StatusEnum.ERROR_PROCESSING))
                .doesNotThrowAnyException();
    }

    @Test
    void sendNotification_deveNaoLancarExcecao_quandoSqsFalha() {
        doThrow(new RuntimeException("SQS error")).when(sqsTemplate).send(anyString(), any());

        // Should NOT throw — notification errors are swallowed
        assertThatCode(() ->
                adapter.sendNotification("user/video.mp4", "meu-video", "userId1", StatusEnum.PROCESSED))
                .doesNotThrowAnyException();
    }

    @Test
    void sendNotification_deveNaoLancarExcecao_quandoJsonFalha() throws Exception {
        doThrow(mock(JsonProcessingException.class)).when(objectMapper).writeValueAsString(any());

        // Should NOT throw — serialization errors are swallowed
        assertThatCode(() ->
                adapter.sendNotification("user/video.mp4", "meu-video", "userId1", StatusEnum.PROCESSED))
                .doesNotThrowAnyException();
    }
}
