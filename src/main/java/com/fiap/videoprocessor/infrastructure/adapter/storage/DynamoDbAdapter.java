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
import java.time.format.DateTimeParseException;
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
    
    private static final String DYNAMODB_UPDATE_ERROR_MSG = "Erro ao atualizar status no DynamoDB: {}";
    
    private final DynamoDbClient dynamoDbClient;
    
    @Value("${dynamodb.table-name:fase5-video-processing}")
    private String tableName;
    
    @Override
    public void updateStatusToProcessing(String videoKey, String userId, String videoId) {
        log.info("Atualizando status para 'uploaded': videoKey={}, userId={}, videoId={}", videoKey, userId, videoId);
        
        try {
            // Agora usamos id (videoId) e userId como chave composta
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(videoId).build());
            key.put("userId", AttributeValue.builder().s(userId).build());
            
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":status", AttributeValue.builder().s(StatusEnum.UPLOADED.getValue()).build());
            expressionAttributeValues.put(":updatedAt", AttributeValue.builder()
                    .s(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).build());
            
            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("SET #status = :status, updatedAt = :updatedAt")
                    .conditionExpression("attribute_exists(id) AND attribute_exists(userId)")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
            
            dynamoDbClient.updateItem(request);
            log.info("Status atualizado para 'uploaded' com sucesso");
            
        } catch (ConditionalCheckFailedException e) {
            log.warn("Registro não encontrado no DynamoDB para atualizar: videoId={}, userId={}", videoId, userId);
        } catch (DynamoDbException e) {
            log.error(DYNAMODB_UPDATE_ERROR_MSG, e.getMessage());
            throw new VideoProcessingException("Erro ao atualizar status no DynamoDB", e);
        }
    }
    
    @Override
    public void updateStatusToSuccess(String videoKey, String processedVideoKey) {
        log.info("Atualizando status para 'processed': videoKey={}", videoKey);
        
        try {
            // Buscar o registro primeiro para obter o userId correto (UUID)
            Optional<VideoDynamoModel> videoOpt = findByVideoKey(videoKey);
            if (videoOpt.isEmpty()) {
                log.warn("Vídeo não encontrado para atualizar status: {}", videoKey);
                return;
            }
            
            VideoDynamoModel video = videoOpt.get();
            
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(video.getId()).build());
            key.put("userId", AttributeValue.builder().s(video.getUserId()).build());
            
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":status", AttributeValue.builder().s(StatusEnum.PROCESSED.getValue()).build());
            expressionAttributeValues.put(":updatedAt", AttributeValue.builder()
                    .s(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).build());
            expressionAttributeValues.put(":processedKey", AttributeValue.builder().s(processedVideoKey).build());
            
            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("SET #status = :status, updatedAt = :updatedAt, processedVideoKey = :processedKey")
                    .conditionExpression("attribute_exists(id) AND attribute_exists(userId)")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
            
            dynamoDbClient.updateItem(request);
            log.info("Status atualizado para 'processed' com sucesso");
            
        } catch (ConditionalCheckFailedException e) {
            log.warn("Registro não encontrado no DynamoDB para atualizar: videoKey={}", videoKey);
        } catch (DynamoDbException e) {
            log.error(DYNAMODB_UPDATE_ERROR_MSG, e.getMessage());
            throw new VideoProcessingException("Erro ao atualizar status no DynamoDB", e);
        }
    }
    
    @Override
    public void updateStatusToError(String videoKey, String errorMessage) {
        log.info("Atualizando status para 'error-processing': videoKey={}", videoKey);
        
        try {
            // Buscar o registro primeiro para obter o userId correto (UUID)
            Optional<VideoDynamoModel> videoOpt = findByVideoKey(videoKey);
            if (videoOpt.isEmpty()) {
                log.warn("Vídeo não encontrado para atualizar status de erro: {}", videoKey);
                return;
            }
            
            VideoDynamoModel video = videoOpt.get();
            
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("id", AttributeValue.builder().s(video.getId()).build());
            key.put("userId", AttributeValue.builder().s(video.getUserId()).build());
            
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":status", AttributeValue.builder().s(StatusEnum.ERROR_PROCESSING.getValue()).build());
            expressionAttributeValues.put(":updatedAt", AttributeValue.builder()
                    .s(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).build());
            expressionAttributeValues.put(":errorMessage", AttributeValue.builder().s(errorMessage).build());
            
            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("SET #status = :status, updatedAt = :updatedAt, errorMessage = :errorMessage")
                    .conditionExpression("attribute_exists(id) AND attribute_exists(userId)")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
            
            dynamoDbClient.updateItem(request);
            log.info("Status atualizado para 'error-processing' com sucesso");
            
        } catch (ConditionalCheckFailedException e) {
            log.warn("Registro não encontrado no DynamoDB para atualizar: videoKey={}", videoKey);
        } catch (DynamoDbException e) {
            log.error(DYNAMODB_UPDATE_ERROR_MSG, e.getMessage());
            // Não lançar exceção aqui para não mascarar o erro original
            log.warn("Continuando processamento apesar do erro no DynamoDB");
        }
    }
    
    @Override
    public Optional<VideoDynamoModel> findByVideoKey(String videoKey) {
        log.info("Buscando vídeo no DynamoDB: videoKey={}", videoKey);
        
        try {
            // Extrair o videoId (id) do videoKey (formato: username/videoId)
            String[] parts = videoKey.split("/");
            if (parts.length != 2) {
                log.warn("Formato de videoKey inválido: {}", videoKey);
                return Optional.empty();
            }
            
            String videoId = parts[1]; // A segunda parte é o id do vídeo
            
            // Query pela partition key (id) - isso retorna todos items com esse id
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":id", AttributeValue.builder().s(videoId).build());
            
            QueryRequest request = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("id = :id")
                    .expressionAttributeValues(expressionAttributeValues)
                    .limit(1)
                    .build();
            
            QueryResponse response = dynamoDbClient.query(request);
            
            if (!response.hasItems() || response.items().isEmpty()) {
                return Optional.empty();
            }
            
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
        } catch (DateTimeParseException e) {
            log.warn("Erro ao parsear data: {}", dateTimeStr);
            return null;
        }
    }
}
