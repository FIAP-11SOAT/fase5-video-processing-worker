package com.fiap.videoprocessor.infrastructure.messaging.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fiap.videoprocessor.domain.model.StatusEnum;

/**
 * DTO para mensagem na fila de notificação
 */
public record MessageQueueDto(
        @JsonProperty("payload")
        PayloadDto payload
) {
    public record PayloadDto(
            @JsonProperty("videoKey")
            String videoKey,
            
            @JsonProperty("videoName")
            String videoName,
            
            @JsonProperty("userId")
            String userId,
            
            @JsonProperty("status")
            String status
    ) {
        public PayloadDto(String videoKey, String videoName, String userId, StatusEnum status) {
            this(videoKey, videoName, userId, status.getValue());
        }
    }
}
