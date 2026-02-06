package com.fiap.videoprocessor.infrastructure.adapter.storage;

import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.domain.model.StatusEnum;
import com.fiap.videoprocessor.domain.model.VideoDynamoModel;
import com.fiap.videoprocessor.domain.ports.output.VideoStatusPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter para interagir com DynamoDB
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamoDbAdapter implements VideoStatusPort {
    
    private final DynamoDbClient dynamoDbClient;
    
    @Value("${dynamodb.table-name:fase5-video-processing}")
    private String tableName;
    
    @Override
    public void updateStatusToProcessing(String videoKey, String userId, String videoId) {
        log.info("Atualizando status para 'uploaded': videoKey={}", videoKey);
        
        try {
            // Buscar o registro existente para obter o userId correto
            Optional<VideoDynamoModel> videoOpt = findByVideoKey(videoKey);
            if (videoOpt.isEmpty()) {
                log.warn("Vídeo não encontrado no DynamoDB para atualizar status: {}", videoKey);
                return;
            }
            
            String existingUserId = videoOpt.get().getUserId();
            log.info("Usando userId existente do registro: {}", existingUserId);
            
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("videoKey", AttributeValue.builder().s(videoKey).build());
            key.put("userId", AttributeValue.builder().s(existingUserId).build());
            
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":status", AttributeValue.builder().s(StatusEnum.UPLOADED.getValue()).build());
            expressionAttributeValues.put(":updatedAt", AttributeValue.builder()
                    .s(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).build());
            
            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("SET #status = :status, updatedAt = :updatedAt")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
            
            dynamoDbClient.updateItem(request);
            log.info("Status atualizado para 'uploaded' com sucesso");
            
        } catch (DynamoDbException e) {
            log.error("Erro ao atualizar status no DynamoDB: {}", e.getMessage());
            throw new VideoProcessingException("Erro ao atualizar status no DynamoDB", e);
        }
    }
    
    @Override
    public void updateStatusToSuccess(String videoKey, String processedVideoKey) {
        log.info("Atualizando status para 'processed': videoKey={}", videoKey);
        
        try {
            // Buscar o registro primeiro para obter o userId
            Optional<VideoDynamoModel> videoOpt = findByVideoKey(videoKey);
            if (videoOpt.isEmpty()) {
                log.warn("Vídeo não encontrado para atualizar status: {}", videoKey);
                return;
            }
            
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("videoKey", AttributeValue.builder().s(videoKey).build());
            key.put("userId", AttributeValue.builder().s(videoOpt.get().getUserId()).build());
            
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":status", AttributeValue.builder().s(StatusEnum.PROCESSED.getValue()).build());
            expressionAttributeValues.put(":updatedAt", AttributeValue.builder()
                    .s(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).build());
            expressionAttributeValues.put(":processedKey", AttributeValue.builder().s(processedVideoKey).build());
            
            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("SET #status = :status, updatedAt = :updatedAt, processedVideoKey = :processedKey")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
            
            dynamoDbClient.updateItem(request);
            log.info("Status atualizado para 'processed' com sucesso");
            
        } catch (DynamoDbException e) {
            log.error("Erro ao atualizar status no DynamoDB: {}", e.getMessage());
            throw new VideoProcessingException("Erro ao atualizar status no DynamoDB", e);
        }
    }
    
    @Override
    public void updateStatusToError(String videoKey, String errorMessage) {
        log.info("Atualizando status para 'error-processing': videoKey={}", videoKey);
        
        try {
            // Buscar o registro primeiro para obter o userId
            Optional<VideoDynamoModel> videoOpt = findByVideoKey(videoKey);
            if (videoOpt.isEmpty()) {
                log.warn("Vídeo não encontrado para atualizar status de erro: {}", videoKey);
                return;
            }
            
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("videoKey", AttributeValue.builder().s(videoKey).build());
            key.put("userId", AttributeValue.builder().s(videoOpt.get().getUserId()).build());
            
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":status", AttributeValue.builder().s(StatusEnum.ERROR_PROCESSING.getValue()).build());
            expressionAttributeValues.put(":updatedAt", AttributeValue.builder()
                    .s(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).build());
            expressionAttributeValues.put(":errorMessage", AttributeValue.builder().s(errorMessage).build());
            
            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("SET #status = :status, updatedAt = :updatedAt, errorMessage = :errorMessage")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
            
            dynamoDbClient.updateItem(request);
            log.info("Status atualizado para 'error-processing' com sucesso");
            
        } catch (DynamoDbException e) {
            log.error("Erro ao atualizar status no DynamoDB: {}", e.getMessage());
            // Não lançar exceção aqui para não mascarar o erro original
            log.warn("Continuando processamento apesar do erro no DynamoDB");
        }
    }
    
    @Override
    public Optional<VideoDynamoModel> findByVideoKey(String videoKey) {
        log.info("Buscando vídeo no DynamoDB: videoKey={}", videoKey);
        
        try {
            // Como a tabela tem chave composta, precisamos fazer um Query
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":videoKey", AttributeValue.builder().s(videoKey).build());
            
            QueryRequest request = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("videoKey = :videoKey")
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
            
            QueryResponse response = dynamoDbClient.query(request);
            
            if (!response.hasItems() || response.items().isEmpty()) {
                return Optional.empty();
            }
            
            // Pegar o primeiro item (deve haver apenas 1)
            Map<String, AttributeValue> item = response.items().get(0);
            
            VideoDynamoModel model = VideoDynamoModel.builder()
                    .videoKey(getStringAttribute(item, "videoKey"))
                    .id(getStringAttribute(item, "id"))
                    .userId(getStringAttribute(item, "userId"))
                    .name(getStringAttribute(item, "name"))
                    .status(getStringAttribute(item, "status"))
                    .createdAt(parseDateTime(getStringAttribute(item, "createdAt")))
                    .updatedAt(parseDateTime(getStringAttribute(item, "updatedAt")))
                    .errorMessage(getStringAttribute(item, "errorMessage"))
                    .processedVideoKey(getStringAttribute(item, "processedVideoKey"))
                    .build();
            
            return Optional.of(model);
            
        } catch (DynamoDbException e) {
            log.error("Erro ao buscar vídeo no DynamoDB: {}", e.getMessage());
            throw new VideoProcessingException("Erro ao buscar vídeo no DynamoDB", e);
        }
    }
    
    private String getStringAttribute(Map<String, AttributeValue> item, String attributeName) {
        AttributeValue value = item.get(attributeName);
        return value != null ? value.s() : null;
    }
    
    private OffsetDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            log.warn("Erro ao parsear data: {}", dateTimeStr);
            return null;
        }
    }
}
