package com.fiap.videoprocessor.domain.ports.output;

import com.fiap.videoprocessor.domain.model.VideoDynamoModel;

import java.util.Optional;

/**
 * Port para gerenciar status de vídeos no DynamoDB
 */
public interface VideoStatusPort {
    
    /**
     * Atualiza o status do vídeo para "processing"
     */
    void updateStatusToProcessing(String videoKey, String userId, String videoId);
    
    /**
     * Atualiza o status do vídeo para "success"
     */
    void updateStatusToSuccess(String videoKey, String processedVideoKey);
    
    /**
     * Atualiza o status do vídeo para "error"
     */
    void updateStatusToError(String videoKey, String errorMessage);
    
    /**
     * Busca informações do vídeo
     */
    Optional<VideoDynamoModel> findByVideoKey(String videoKey);
}
