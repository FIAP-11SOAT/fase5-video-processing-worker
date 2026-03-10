package com.fiap.videoprocessor.domain.ports.output;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Porta de saída para armazenamento de vídeos (S3)
 */
public interface VideoStoragePort {
    /**
     * Baixa um vídeo do S3 para um arquivo local
     */
    Path downloadVideo(String bucket, String key, Path destinationPath);
    
    /**
     * Baixa um vídeo do S3 como InputStream
     */
    InputStream downloadVideoAsStream(String bucket, String key);
    
    /**
     * Faz upload de um arquivo para o S3
     */
    String uploadFile(String bucket, String key, Path filePath);
    
    /**
     * Faz upload de um arquivo a partir de um InputStream (escolhe automaticamente entre simples ou multipart)
     */
    String uploadFileFromStream(String bucket, String key, InputStream inputStream, long contentLength);
    
    /**
     * Faz upload de um arquivo a partir de um InputStream usando multipart upload
     */
    String uploadFileMultipart(String bucket, String key, InputStream inputStream, long contentLength);
    
    /**
     * Verifica se um arquivo existe no S3
     */
    boolean fileExists(String bucket, String key);
}
