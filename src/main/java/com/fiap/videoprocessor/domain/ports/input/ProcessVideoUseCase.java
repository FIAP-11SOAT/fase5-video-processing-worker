package com.fiap.videoprocessor.domain.ports.input;

import com.fiap.videoprocessor.domain.model.ProcessingResult;

/**
 * Porta de entrada - Caso de uso principal para processar vídeos
 */
public interface ProcessVideoUseCase {
    /**
     * Processa um vídeo do S3, extraindo frames e salvando no S3
     * 
     * @param videoId ID do vídeo a ser processado
     * @param s3Key Chave do arquivo no S3
     * @param inputBucket Bucket do S3 onde está o vídeo
     * @param outputBucket Bucket do S3 onde os frames serão salvos
     * @return Resultado do processamento
     */
    ProcessingResult process(String videoId, String s3Key, String inputBucket, String outputBucket);
}
