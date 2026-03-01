package com.fiap.videoprocessor.infrastructure.adapter.storage;

import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.domain.ports.output.VideoStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter de infraestrutura para AWS S3
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class S3StorageAdapter implements VideoStoragePort {
    
    private final S3Client s3Client;
    
    @Value("${s3.multipart.part-size:10485760}") // 10MB padrão
    private long multipartPartSize;
    
    @Override
    public Path downloadVideo(String bucket, String key, Path destinationPath) {
        log.info("Baixando arquivo do S3: bucket={}, key={}", bucket, key);
        
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest);
            
            Files.createDirectories(destinationPath.getParent());
            Files.copy(response, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            
            log.info("Arquivo baixado com sucesso: {}", destinationPath);
            return destinationPath;
            
        } catch (S3Exception e) {
            log.error("Erro ao baixar arquivo do S3: {}", e.awsErrorDetails().errorMessage());
            throw new VideoProcessingException("Erro ao baixar vídeo do S3: " + e.awsErrorDetails().errorMessage(), e);
        } catch (IOException e) {
            log.error("Erro ao salvar arquivo localmente: {}", e.getMessage());
            throw new VideoProcessingException("Erro ao salvar arquivo localmente", e);
        }
    }
    
    @Override
    public InputStream downloadVideoAsStream(String bucket, String key) {
        log.info("Baixando arquivo do S3 como stream: bucket={}, key={}", bucket, key);
        
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            return s3Client.getObject(getObjectRequest);
            
        } catch (S3Exception e) {
            log.error("Erro ao baixar arquivo do S3: {}", e.awsErrorDetails().errorMessage());
            throw new VideoProcessingException("Erro ao baixar vídeo do S3: " + e.awsErrorDetails().errorMessage(), e);
        }
    }
    
    @Override
    public String uploadFile(String bucket, String key, Path filePath) {
        log.info("Fazendo upload do arquivo para S3: bucket={}, key={}", bucket, key);
        
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/zip")
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromFile(filePath));
            
            log.info("Upload concluído: s3://{}/{}", bucket, key);
            return key;
            
        } catch (S3Exception e) {
            log.error("Erro ao fazer upload para S3: {}", e.awsErrorDetails().errorMessage());
            throw new VideoProcessingException("Erro ao fazer upload para S3: " + e.awsErrorDetails().errorMessage(), e);
        }
    }
    
    @Override
    public String uploadFileFromStream(String bucket, String key, InputStream inputStream, long contentLength) {
        // AWS S3 requer que cada parte do multipart upload (exceto a última) tenha no mínimo 5MB
        final long MIN_MULTIPART_SIZE = 5 * 1024 * 1024; // 5MB
        
        if (contentLength < MIN_MULTIPART_SIZE) {
            log.info("Arquivo pequeno ({} bytes), usando upload simples", contentLength);
            return uploadFileSimple(bucket, key, inputStream, contentLength);
        } else {
            log.info("Arquivo grande ({} bytes), usando multipart upload", contentLength);
            return uploadFileMultipart(bucket, key, inputStream, contentLength);
        }
    }
    
    private String uploadFileSimple(String bucket, String key, InputStream inputStream, long contentLength) {
        log.info("Fazendo upload simples para S3: bucket={}, key={}, tamanho={} bytes", bucket, key, contentLength);
        
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/zip")
                    .contentLength(contentLength)
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));
            
            log.info("Upload simples concluído: s3://{}/{}", bucket, key);
            return key;
            
        } catch (S3Exception e) {
            log.error("Erro ao fazer upload simples para S3: {}", e.awsErrorDetails().errorMessage());
            throw new VideoProcessingException("Erro ao fazer upload simples para S3: " + e.awsErrorDetails().errorMessage(), e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                log.warn("Erro ao fechar InputStream", e);
            }
        }
    }
    
    @Override
    public String uploadFileMultipart(String bucket, String key, InputStream inputStream, long contentLength) {
        log.info("Iniciando multipart upload para S3: bucket={}, key={}, tamanho estimado={} bytes", 
                bucket, key, contentLength);
        
        String uploadId = null;
        List<CompletedPart> completedParts = new ArrayList<>();
        
        try {
            // 1. Iniciar multipart upload
            CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/zip")
                    .build();
            
            CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
            uploadId = createResponse.uploadId();
            log.info("Multipart upload iniciado: uploadId={}", uploadId);
            
            // 2. Fazer upload das partes
            byte[] buffer = new byte[(int) multipartPartSize];
            int partNumber = 1;
            int bytesRead;
            long totalBytesUploaded = 0;
            
            while ((bytesRead = inputStream.read(buffer)) > 0) {
                log.debug("Fazendo upload da parte {}: {} bytes", partNumber, bytesRead);
                
                // Criar array com tamanho exato dos bytes lidos
                byte[] partData = bytesRead < buffer.length 
                        ? java.util.Arrays.copyOf(buffer, bytesRead)
                        : buffer;
                
                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build();
                
                UploadPartResponse uploadPartResponse = s3Client.uploadPart(
                        uploadPartRequest,
                        RequestBody.fromBytes(partData)
                );
                
                CompletedPart completedPart = CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(uploadPartResponse.eTag())
                        .build();
                
                completedParts.add(completedPart);
                totalBytesUploaded += bytesRead;
                
                log.info("Parte {} enviada com sucesso. Total: {} bytes", partNumber, totalBytesUploaded);
                partNumber++;
            }
            
            // 3. Completar multipart upload
            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();
            
            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(completedMultipartUpload)
                    .build();
            
            s3Client.completeMultipartUpload(completeRequest);
            
            log.info("Multipart upload concluído com sucesso: s3://{}/{} ({} partes, {} bytes totais)", 
                    bucket, key, completedParts.size(), totalBytesUploaded);
            
            return key;
            
        } catch (IOException e) {
            log.error("Erro ao ler InputStream: {}", e.getMessage());
            abortMultipartUpload(bucket, key, uploadId);
            throw new VideoProcessingException("Erro ao ler dados para upload", e);
        } catch (S3Exception e) {
            log.error("Erro no multipart upload para S3: {}", e.awsErrorDetails().errorMessage());
            abortMultipartUpload(bucket, key, uploadId);
            throw new VideoProcessingException("Erro no multipart upload: " + e.awsErrorDetails().errorMessage(), e);
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                log.warn("Erro ao fechar InputStream", e);
            }
        }
    }
    
    private void abortMultipartUpload(String bucket, String key, String uploadId) {
        if (uploadId != null) {
            try {
                log.warn("Abortando multipart upload: uploadId={}", uploadId);
                AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .build();
                s3Client.abortMultipartUpload(abortRequest);
            } catch (Exception e) {
                log.error("Erro ao abortar multipart upload", e);
            }
        }
    }
    
    @Override
    public boolean fileExists(String bucket, String key) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            s3Client.headObject(headObjectRequest);
            return true;
            
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.error("Erro ao verificar existência do arquivo no S3: {}", e.awsErrorDetails().errorMessage());
            throw new VideoProcessingException("Erro ao verificar arquivo no S3", e);
        }
    }
    
    /**
     * Busca os metadados de um objeto no S3
     */
    public java.util.Map<String, String> getObjectMetadata(String bucket, String key) {
        log.info("Buscando metadados do objeto S3: bucket={}, key={}", bucket, key);
        
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            
            HeadObjectResponse response = s3Client.headObject(headObjectRequest);
            
            log.debug("Metadados obtidos: {}", response.metadata());
            return response.metadata();
            
        } catch (S3Exception e) {
            log.error("Erro ao buscar metadados do objeto S3: {}", e.awsErrorDetails().errorMessage());
            throw new VideoProcessingException("Erro ao buscar metadados do objeto S3", e);
        }
    }
}
