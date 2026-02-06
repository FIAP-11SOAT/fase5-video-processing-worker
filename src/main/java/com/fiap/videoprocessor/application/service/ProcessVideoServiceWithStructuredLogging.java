package com.fiap.videoprocessor.application.service;

import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.domain.model.Frame;
import com.fiap.videoprocessor.domain.model.ProcessingResult;
import com.fiap.videoprocessor.domain.model.StatusEnum;
import com.fiap.videoprocessor.domain.ports.input.ProcessVideoUseCase;
import com.fiap.videoprocessor.domain.ports.output.FileCompressionPort;
import com.fiap.videoprocessor.domain.ports.output.NotificationPort;
import com.fiap.videoprocessor.domain.ports.output.VideoProcessorPort;
import com.fiap.videoprocessor.domain.ports.output.VideoStatusPort;
import com.fiap.videoprocessor.domain.ports.output.VideoStoragePort;
import com.fiap.videoprocessor.infrastructure.messaging.model.VideoProcessingMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.*;


@RequiredArgsConstructor
@Slf4j
public class ProcessVideoServiceWithStructuredLogging implements ProcessVideoUseCase {
    
    private final VideoStoragePort videoStoragePort;
    private final VideoProcessorPort videoProcessorPort;
    private final FileCompressionPort fileCompressionPort;
    private final VideoStatusPort videoStatusPort;
    private final NotificationPort notificationPort;
    
    @Value("${video.processing.fps:1}")
    private int framesPerSecond;
    
    @Value("${video.processing.temp-dir:./temp}")
    private String tempDir;
    
    @Value("${s3.output-bucket:fase5-videos-processed}")
    private String outputBucket;
    
    @Override
    public void processVideo(VideoProcessingMessage message) {
        String videoKey = message.getKey();
        
        // ✅ BOM: Structured logging com campos indexáveis
        log.info("Video processing started", 
                keyValue("bucket", message.getBucket()),
                keyValue("videoKey", videoKey),
                keyValue("userId", message.getUserId()),
                keyValue("videoId", message.getVideoId()),
                keyValue("stage", "initialization")
        );
        
        try {
            // Atualizar status para "processing"
            videoStatusPort.updateStatusToProcessing(videoKey, message.getUserId(), message.getVideoId());
            
            log.info("Status updated to processing",
                    keyValue("videoKey", videoKey),
                    keyValue("userId", message.getUserId()),
                    keyValue("status", "processing")
            );
            
            // Processar vídeo
            ProcessingResult result = process(
                    message.getVideoId(), 
                    videoKey, 
                    message.getBucket(), 
                    outputBucket
            );
            
            // Atualizar status para "success"
            videoStatusPort.updateStatusToSuccess(videoKey, result.getZipS3Key());
            
            // Enviar notificação de sucesso
            notificationPort.sendNotification(videoKey, message.getVideoId(), message.getUserId(), StatusEnum.PROCESSED);
            
            // ✅ BOM: Log de sucesso com métricas
            log.info("Video processed successfully", 
                    keyValue("videoKey", videoKey),
                    keyValue("userId", message.getUserId()),
                    keyValue("videoId", message.getVideoId()),
                    keyValue("framesGenerated", result.getFrameCount()),
                    keyValue("zipS3Key", result.getZipS3Key()),
                    keyValue("status", "success")
            );
            
        } catch (Exception e) {
            // ✅ BOM: Log de erro com contexto completo
            log.error("Video processing failed", 
                    keyValue("videoKey", videoKey),
                    keyValue("userId", message.getUserId()),
                    keyValue("videoId", message.getVideoId()),
                    keyValue("errorType", e.getClass().getSimpleName()),
                    keyValue("errorMessage", e.getMessage()),
                    keyValue("status", "error"),
                    e  // Exception no final para stack trace
            );
            
            try {
                videoStatusPort.updateStatusToError(videoKey, e.getMessage());
                notificationPort.sendNotification(videoKey, message.getVideoId(), message.getUserId(), StatusEnum.ERROR_PROCESSING);
            } catch (Exception dbError) {
                log.error("Failed to update error status", 
                        keyValue("videoKey", videoKey),
                        keyValue("originalError", e.getMessage()),
                        keyValue("dbError", dbError.getMessage())
                );
            }
            
            throw new VideoProcessingException("Failed to process video: " + e.getMessage(), e);
        }
    }
    
    @Override
    public ProcessingResult process(String videoId, String s3Key, String inputBucket, String outputBucket) {
        // ✅ BOM: Log de início com parâmetros estruturados
        log.info("Starting video frame extraction",
                keyValue("videoId", videoId),
                keyValue("s3Key", s3Key),
                keyValue("inputBucket", inputBucket),
                keyValue("outputBucket", outputBucket),
                keyValue("fps", framesPerSecond)
        );
        
        long startTime = System.currentTimeMillis();
        Path workDir = null;
        
        try {
            // Criar diretório temporário
            workDir = createWorkDirectory();
            log.debug("Work directory created",
                    keyValue("workDir", workDir.toString()),
                    keyValue("videoId", videoId)
            );
            
            // Download do vídeo
            Path videoPath = downloadVideo(inputBucket, s3Key, workDir);
            long downloadTime = System.currentTimeMillis() - startTime;
            
            // ✅ BOM: Log com métrica de performance
            log.info("Video downloaded",
                    keyValue("videoId", videoId),
                    keyValue("videoPath", videoPath.toString()),
                    keyValue("downloadTimeMs", downloadTime),
                    keyValue("fileSizeBytes", Files.size(videoPath))
            );
            
            // Extrair frames
            long extractStartTime = System.currentTimeMillis();
            List<Frame> frames = extractFrames(videoPath, videoId);
            long extractTime = System.currentTimeMillis() - extractStartTime;
            
            // ✅ BOM: Log com métrica de extração
            log.info("Frames extracted",
                    keyValue("videoId", videoId),
                    keyValue("framesCount", frames.size()),
                    keyValue("extractTimeMs", extractTime),
                    keyValue("averageTimePerFrame", extractTime / frames.size())
            );
            
            // Salvar frames
            List<String> framePaths = saveFrames(frames, workDir);
            
            // Comprimir frames
            long compressStartTime = System.currentTimeMillis();
            Path zipPath = compressFrames(framePaths, workDir, videoId);
            long compressTime = System.currentTimeMillis() - compressStartTime;
            
            // ✅ BOM: Log com métrica de compressão
            log.info("Frames compressed",
                    keyValue("videoId", videoId),
                    keyValue("zipPath", zipPath.toString()),
                    keyValue("compressTimeMs", compressTime),
                    keyValue("zipSizeBytes", Files.size(zipPath))
            );
            
            // Upload do ZIP
            long uploadStartTime = System.currentTimeMillis();
            String zipS3Key = uploadZip(zipPath, outputBucket, videoId);
            long uploadTime = System.currentTimeMillis() - uploadStartTime;
            
            // ✅ BOM: Log com métrica de upload
            log.info("ZIP uploaded to S3",
                    keyValue("videoId", videoId),
                    keyValue("zipS3Key", zipS3Key),
                    keyValue("outputBucket", outputBucket),
                    keyValue("uploadTimeMs", uploadTime)
            );
            
            long totalTime = System.currentTimeMillis() - startTime;
            
            // ✅ EXCELENTE: Log resumo com todas as métricas
            log.info("Video processing completed",
                    keyValue("videoId", videoId),
                    keyValue("totalTimeMs", totalTime),
                    keyValue("downloadTimeMs", downloadTime),
                    keyValue("extractTimeMs", extractTime),
                    keyValue("compressTimeMs", compressTime),
                    keyValue("uploadTimeMs", uploadTime),
                    keyValue("framesGenerated", frames.size()),
                    keyValue("zipS3Key", zipS3Key),
                    keyValue("performance", "ok")  // Para métricas
            );
            
            return ProcessingResult.builder()
                    .videoId(videoId)
                    .frameCount(frames.size())
                    .zipS3Key(zipS3Key)
                    .success(true)
                    .processingTimeMs(totalTime)
                    .build();
                    
        } catch (Exception e) {
            long failureTime = System.currentTimeMillis() - startTime;
            
            // ✅ BOM: Log de erro com contexto e tempo até falha
            log.error("Video processing failed",
                    keyValue("videoId", videoId),
                    keyValue("s3Key", s3Key),
                    keyValue("timeBeforeFailureMs", failureTime),
                    keyValue("errorType", e.getClass().getSimpleName()),
                    keyValue("errorMessage", e.getMessage()),
                    e
            );
            
            throw new VideoProcessingException("Failed to process video: " + e.getMessage(), e);
            
        } finally {
            // Limpar diretório temporário
            if (workDir != null) {
                cleanupWorkDirectory(workDir);
                log.debug("Work directory cleaned",
                        keyValue("workDir", workDir.toString()),
                        keyValue("videoId", videoId)
                );
            }
        }
    }
    
    // Métodos auxiliares...
    
    private Path createWorkDirectory() throws IOException {
        String uniqueId = UUID.randomUUID().toString();
        Path dir = Path.of(tempDir, uniqueId);
        Files.createDirectories(dir);
        return dir;
    }
    
    private Path downloadVideo(String bucket, String key, Path workDir) {
        // Implementação...
        return null;
    }
    
    private List<Frame> extractFrames(Path videoPath, String videoId) {
        // Implementação...
        return null;
    }
    
    private List<String> saveFrames(List<Frame> frames, Path workDir) {
        // Implementação...
        return null;
    }
    
    private Path compressFrames(List<String> framePaths, Path workDir, String videoId) {
        // Implementação...
        return null;
    }
    
    private String uploadZip(Path zipPath, String bucket, String videoId) {
        // Implementação...
        return null;
    }
    
    private void cleanupWorkDirectory(Path workDir) {
        // Implementação...
    }
}

/**
 * QUERIES ÚTEIS NO CLOUDWATCH LOGS INSIGHTS:
 * 
 * 1. Rastrear processamento de vídeo específico:
 * --------------------------------------------
 * fields @timestamp, message, stage, framesCount, totalTimeMs
 * | filter videoId = "abc123"
 * | sort @timestamp asc
 * 
 * 
 * 2. Análise de performance:
 * -------------------------
 * fields @timestamp, videoId, totalTimeMs, downloadTimeMs, extractTimeMs, compressTimeMs, uploadTimeMs
 * | filter message = "Video processing completed"
 * | stats avg(totalTimeMs) as avg_total, 
 *         avg(downloadTimeMs) as avg_download,
 *         avg(extractTimeMs) as avg_extract,
 *         avg(compressTimeMs) as avg_compress,
 *         avg(uploadTimeMs) as avg_upload
 * 
 * 
 * 3. Identificar gargalos de performance:
 * --------------------------------------
 * fields @timestamp, videoId, totalTimeMs
 * | filter message = "Video processing completed"
 * | filter totalTimeMs > 60000
 * | sort totalTimeMs desc
 * 
 * 
 * 4. Taxa de sucesso/erro:
 * -----------------------
 * fields @timestamp, message, status
 * | filter message in ["Video processing completed", "Video processing failed"]
 * | stats count() by status
 * 
 * 
 * 5. Erros por tipo:
 * -----------------
 * fields @timestamp, videoId, errorType, errorMessage
 * | filter level = "ERROR"
 * | stats count() by errorType
 * | sort count desc
 * 
 * 
 * 6. Vídeos processados por hora:
 * ------------------------------
 * fields @timestamp
 * | filter message = "Video processing completed"
 * | stats count() by bin(1h)
 * 
 * 
 * 7. Performance por usuário:
 * --------------------------
 * fields @timestamp, userId, totalTimeMs
 * | filter message = "Video processing completed"
 * | stats avg(totalTimeMs) as avg_time, count() as total_videos by userId
 * | sort total_videos desc
 */
