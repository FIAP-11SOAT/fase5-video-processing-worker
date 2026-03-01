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

/**
 * Implementação do caso de uso de processamento de vídeo
 * Camada de aplicação - orquestra as operações do domínio
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessVideoService implements ProcessVideoUseCase {
    
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
        log.info("Iniciando processamento do vídeo: bucket={}, key={}, userId={}, videoId={}", 
                message.getBucket(), videoKey, message.getUserId(), message.getVideoId());
        
        try {
            // Atualizar status para "processing" no DynamoDB
            videoStatusPort.updateStatusToProcessing(videoKey, message.getUserId(), message.getVideoId());
            
            // Processar vídeo
            ProcessingResult result = process(
                    message.getVideoId(), 
                    videoKey, 
                    message.getBucket(), 
                    outputBucket
            );
            
            // Atualizar status para "success" no DynamoDB
            videoStatusPort.updateStatusToSuccess(videoKey, result.getZipS3Key());
            
            // Enviar notificação de sucesso com o nome real do vídeo
            notificationPort.sendNotification(videoKey, message.getVideoName(), message.getUserId(), StatusEnum.PROCESSED);
            
            log.info("Vídeo processado e status atualizado com sucesso: {}", videoKey);
            
        } catch (Exception e) {
            log.error("Erro ao processar vídeo: {}", e.getMessage(), e);
            
            // Atualizar status para "error" no DynamoDB
            try {
                videoStatusPort.updateStatusToError(videoKey, e.getMessage());
                
                // Enviar notificação de erro com o nome real do vídeo
                notificationPort.sendNotification(videoKey, message.getVideoName(), message.getUserId(), StatusEnum.ERROR_PROCESSING);
            } catch (Exception dbError) {
                log.error("Erro adicional ao atualizar status de erro no DynamoDB: {}", dbError.getMessage());
            }
            
            // Re-lançar exceção para que a mensagem retorne à fila
            throw new VideoProcessingException("Erro ao processar vídeo: " + e.getMessage(), e);
        }
    }
    
    @Override
    public ProcessingResult process(String videoId, String s3Key, String inputBucket, String outputBucket) {
        log.info("Iniciando processamento do vídeo: videoId={}, s3Key={}, inputBucket={}, outputBucket={}", 
                videoId, s3Key, inputBucket, outputBucket);
        
        long startTime = System.currentTimeMillis();
        Path workDir = null;
        
        try {
            // 1. Criar diretório de trabalho temporário (apenas para frames)
            workDir = createWorkDirectory(videoId);
            log.info("Diretório de trabalho criado: {}", workDir);
            
            // 2. Baixar vídeo do S3 diretamente para arquivo temporário
            log.info("Baixando vídeo do S3 (bucket: {})...", inputBucket);
            Path videoPath = workDir.resolve("video.mp4");
            videoStoragePort.downloadVideo(inputBucket, s3Key, videoPath);
            log.info("Vídeo baixado com sucesso");
            
            // 3. Extrair frames usando FFmpeg
            log.info("Extraindo frames do vídeo ({}fps)...", framesPerSecond);
            Path framesDir = workDir.resolve("frames");
            Files.createDirectories(framesDir);
            List<Frame> frames = videoProcessorPort.extractFrames(videoPath, framesDir, framesPerSecond);
            log.info("Extraídos {} frames", frames.size());
            
            if (frames.isEmpty()) {
                throw new VideoProcessingException("Nenhum frame foi extraído do vídeo");
            }
            
            // 4. Determinar o caminho de saída do ZIP baseado na estrutura do vídeo de entrada
            log.info("Comprimindo e fazendo upload do ZIP para S3 (bucket: {})...", outputBucket);
            
            // Extrair o diretório e nome do arquivo do s3Key
            // Exemplo: "moribeiro/487812f9-333f-46c8-abc7-7c3b4da47e8c.mp4" 
            // -> diretório: "moribeiro", arquivo: "487812f9-333f-46c8-abc7-7c3b4da47e8c"
            String zipS3Key;
            int lastSlashIndex = s3Key.lastIndexOf('/');
            if (lastSlashIndex > 0) {
                // Tem diretório no caminho
                String directory = s3Key.substring(0, lastSlashIndex);
                String fileName = s3Key.substring(lastSlashIndex + 1);
                String fileNameWithoutExt = fileName.contains(".") 
                    ? fileName.substring(0, fileName.lastIndexOf('.'))
                    : fileName;
                zipS3Key = directory + "/" + fileNameWithoutExt + ".zip";
            } else {
                // Sem diretório, usar apenas o nome do arquivo
                String fileNameWithoutExt = s3Key.contains(".") 
                    ? s3Key.substring(0, s3Key.lastIndexOf('.'))
                    : s3Key;
                zipS3Key = fileNameWithoutExt + ".zip";
            }
            
            log.info("ZIP será salvo em: {}", zipS3Key);
            
            List<Path> framePaths = frames.stream()
                    .map(f -> framesDir.resolve(f.getFileName()))
                    .toList();
            
            // Comprimir frames em arquivo temporário
            Path zipPath = workDir.resolve("frames.zip");
            fileCompressionPort.compressFiles(framePaths, zipPath);
            long zipSize = Files.size(zipPath);
            log.info("ZIP criado localmente: {} ({} bytes)", zipPath, zipSize);
            
            // Fazer upload do ZIP para S3
            log.info("Fazendo upload do ZIP para S3...");
            videoStoragePort.uploadFile(outputBucket, zipS3Key, zipPath);
            log.info("ZIP enviado para S3: {}", zipS3Key);
            
            // Deletar vídeo temporário imediatamente após processar
            Files.deleteIfExists(videoPath);
            
            // Deletar ZIP temporário após upload
            Files.deleteIfExists(zipPath);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            ProcessingResult result = ProcessingResult.builder()
                    .videoId(videoId)
                    .success(true)
                    .message(String.format("Processamento concluído! %d frames extraídos.", frames.size()))
                    .frameCount(frames.size())
                    .frames(frames)
                    .zipS3Key(zipS3Key)
                    .processingTimeMs(processingTime)
                    .build();
            
            log.info("Processamento concluído com sucesso em {}ms", processingTime);
            return result;
            
        } catch (Exception e) {
            log.error("Erro ao processar vídeo: {}", e.getMessage(), e);
            throw new VideoProcessingException("Erro ao processar vídeo: " + e.getMessage(), e);
        } finally {
            // Limpar diretório temporário
            if (workDir != null) {
                cleanupWorkDirectory(workDir);
            }
        }
    }
    
    private Path createWorkDirectory(String videoId) {
        try {
            Path baseDir = Path.of(tempDir);
            Files.createDirectories(baseDir);
            return Files.createTempDirectory(baseDir, "video_" + videoId + "_");
        } catch (IOException e) {
            throw new VideoProcessingException("Erro ao criar diretório de trabalho", e);
        }
    }
    
    private void cleanupWorkDirectory(Path workDir) {
        try {
            log.info("Limpando diretório temporário: {}", workDir);
            Files.walk(workDir)
                    .sorted((a, b) -> b.compareTo(a)) // Ordem reversa para deletar arquivos antes de diretórios
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("Não foi possível deletar: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("Erro ao limpar diretório temporário", e);
        }
    }
}
