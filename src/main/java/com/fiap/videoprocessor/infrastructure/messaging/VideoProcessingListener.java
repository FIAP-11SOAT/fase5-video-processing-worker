package com.fiap.videoprocessor.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.videoprocessor.domain.ports.input.ProcessVideoUseCase;
import com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage;
import com.fiap.videoprocessor.infrastructure.messaging.model.VideoProcessingMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Listener para mensagens da fila SQS de processamento de vídeos
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VideoProcessingListener {
    
    private final ProcessVideoUseCase processVideoUseCase;
    private final ObjectMapper objectMapper;
    
    @SqsListener(value = "${sqs.queue-name:fase5-video-processing-queue}")
    public void processMessage(String message) {
        log.info("Mensagem recebida da fila SQS");
        log.debug("Payload: {}", message);
        
        try {
            // Parse da mensagem S3 Event
            S3EventMessage s3Event = objectMapper.readValue(message, S3EventMessage.class);
            
            if (s3Event.getRecords() == null || s3Event.getRecords().isEmpty()) {
                log.warn("Mensagem não contém records válidos");
                return;
            }
            
            // Processar cada record (geralmente vem apenas 1)
            for (S3EventMessage.S3EventRecord record : s3Event.getRecords()) {
                processRecord(record);
            }
            
        } catch (Exception e) {
            log.error("Erro ao processar mensagem da fila: {}", e.getMessage(), e);
            // A mensagem será retornada para a fila após visibility timeout
            throw new RuntimeException("Erro ao processar mensagem", e);
        }
    }
    
    private void processRecord(S3EventMessage.S3EventRecord record) {
        try {
            String bucket = record.getS3().getBucket().getName();
            String key = record.getS3().getObject().getKey();
            Long size = record.getS3().getObject().getSize();
            
            log.info("Processando vídeo: bucket={}, key={}, size={}", bucket, key, size);
            
            // Extrair userId e videoId da key (formato: userId/videoId)
            String[] keyParts = key.split("/");
            if (keyParts.length != 2) {
                log.error("Formato de key inválido. Esperado: userId/videoId, recebido: {}", key);
                throw new IllegalArgumentException("Formato de key inválido: " + key);
            }
            
            String userId = keyParts[0];
            String videoId = keyParts[1];
            
            log.info("Extraído: userId={}, videoId={}", userId, videoId);
            
            // Criar mensagem de processamento
            VideoProcessingMessage processingMessage = VideoProcessingMessage.builder()
                    .bucket(bucket)
                    .key(key)
                    .userId(userId)
                    .videoId(videoId)
                    .size(size)
                    .build();
            
            // Processar vídeo
            processVideoUseCase.processVideo(processingMessage);
            
            log.info("Vídeo processado com sucesso: {}", key);
            
        } catch (Exception e) {
            log.error("Erro ao processar record: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao processar record", e);
        }
    }
}
