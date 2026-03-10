package com.fiap.videoprocessor.domain.ports.output;

import com.fiap.videoprocessor.domain.model.StatusEnum;

/**
 * Port para enviar notificações
 */
public interface NotificationPort {
    
    /**
     * Envia notificação de processamento concluído
     */
    void sendNotification(String videoKey, String videoName, String userId, StatusEnum status);
}
