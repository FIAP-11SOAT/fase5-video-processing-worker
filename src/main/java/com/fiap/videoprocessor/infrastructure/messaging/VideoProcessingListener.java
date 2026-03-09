package com.fiap.videoprocessor.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.domain.model.VideoDynamoModel;
import com.fiap.videoprocessor.domain.ports.input.ProcessVideoUseCase;
import com.fiap.videoprocessor.domain.ports.output.VideoStatusPort;
import com.fiap.videoprocessor.infrastructure.messaging.model.S3EventMessage;
import com.fiap.videoprocessor.infrastructure.messaging.model.VideoProcessingMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Listener para mensagens da fila SQS de processamento de vídeos
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VideoProcessingListener {
    
    private final ProcessVideoUseCase processVideoUseCase;
    private final ObjectMapper objectMapper;
    private final VideoStatusPort videoStatusPort;
    
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
            throw new VideoProcessingException("Erro ao processar mensagem", e);
        }
    }
    
    private void processRecord(S3EventMessage.S3EventRecord record) {
        try {
            String bucket = record.getS3().getBucket().getName();
            String key = record.getS3().getObject().getKey();
            Long size = record.getS3().getObject().getSize();
            
            log.info("Processando vídeo: bucket={}, key={}, size={}", bucket, key, size);
            
            // Extrair username e videoId da key (formato: username/videoId)
            String[] keyParts = key.split("/");
            if (keyParts.length != 2) {
                log.error("Formato de key inválido. Esperado: username/videoId, recebido: {}", key);
                throw new IllegalArgumentException("Formato de key inválido: " + key);
            }
            
            String username = keyParts[0];
            String videoId = keyParts[1];
            
            log.info("Extraído da key: username={}, videoId={}", username, videoId);
            
            // Buscar o userId e videoName real do DynamoDB
            String userId = username; // Valor padrão caso não encontre no DynamoDB
            String videoName = videoId; // Valor padrão caso não encontre no DynamoDB
            
            try {
                log.info("Buscando userId e videoName no DynamoDB para videoKey: {}", key);
                Optional<VideoDynamoModel> videoOpt = videoStatusPort.findByVideoKey(key);
                
                if (videoOpt.isPresent()) {
                    VideoDynamoModel video = videoOpt.get();
                    userId = video.getUserId();
                    videoName = video.getName() != null ? video.getName() : videoId;
                    log.info("UserId obtido do DynamoDB: {}", userId);
                    log.info("VideoName obtido do DynamoDB: {}", videoName);
                } else {
                    log.warn("Vídeo não encontrado no DynamoDB. Usando username como fallback: {}", username);
                }
            } catch (Exception e) {
                log.error("Erro ao buscar vídeo no DynamoDB. Usando username como fallback: {}", username, e);
            }
            
            log.info("UserId final que será usado: {}", userId);
            log.info("VideoName final que será usado: {}", videoName);
            
            // Criar mensagem de processamento
            VideoProcessingMessage processingMessage = VideoProcessingMessage.builder()
                    .bucket(bucket)
                    .key(key)
                    .userId(userId)
                    .videoId(videoId)
                    .videoName(videoName)
                    .size(size)
                    .build();
            
            // Processar vídeo
            processVideoUseCase.processVideo(processingMessage);
            
            log.info("Vídeo processado com sucesso: {}", key);
            
        } catch (Exception e) {
            log.error("Erro ao processar record: {}", e.getMessage(), e);
            throw new VideoProcessingException("Erro ao processar record", e);
        }
    }
}