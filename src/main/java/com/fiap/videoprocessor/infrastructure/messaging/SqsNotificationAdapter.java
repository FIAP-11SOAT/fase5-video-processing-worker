package com.fiap.videoprocessor.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.videoprocessor.domain.model.StatusEnum;
import com.fiap.videoprocessor.domain.ports.output.NotificationPort;
import com.fiap.videoprocessor.infrastructure.messaging.model.MessageQueueDto;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adapter para enviar mensagens para fila SQS de notificação
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SqsNotificationAdapter implements NotificationPort {
    
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${sqs.notification-queue-name:fase5-video-notification-queue}")
    private String notificationQueueName;
    
    @Override
    public void sendNotification(String videoKey, String videoName, String userId, StatusEnum status) {
        log.info("Enviando notificação: videoKey={}, userId={}, status={}", videoKey, userId, status.getValue());
        
        try {
            MessageQueueDto.PayloadDto payload = new MessageQueueDto.PayloadDto(
                    videoKey,
                    videoName,
                    userId,
                    status
            );
            
            MessageQueueDto message = new MessageQueueDto(payload);
            
            String messageJson = objectMapper.writeValueAsString(message);
            log.debug("Mensagem JSON: {}", messageJson);
            
            sqsTemplate.send(notificationQueueName, message);
            
            log.info("Notificação enviada com sucesso para a fila: {}", notificationQueueName);
            
        } catch (Exception e) {
            log.error("Erro ao enviar notificação: {}", e.getMessage(), e);
            // Não lançar exceção para não interromper o fluxo principal
        }
    }
}
