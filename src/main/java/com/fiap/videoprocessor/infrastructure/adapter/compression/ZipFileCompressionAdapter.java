package com.fiap.videoprocessor.infrastructure.adapter.compression;

import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.domain.ports.output.FileCompressionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Adapter para compressão de arquivos em ZIP
 */
@Component
@Slf4j
public class ZipFileCompressionAdapter implements FileCompressionPort {
    
    private static final int BUFFER_SIZE = 8192;
    
    @Override
    public Path compressFiles(List<Path> files, Path outputZipPath) {
        log.info("Comprimindo {} arquivos em: {}", files.size(), outputZipPath);
        
        try (FileOutputStream fos = new FileOutputStream(outputZipPath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            for (Path file : files) {
                if (!file.toFile().exists()) {
                    log.warn("Arquivo não encontrado, ignorando: {}", file);
                    continue;
                }
                
                addFileToZip(zos, file);
            }
            
            log.info("Compressão concluída: {}", outputZipPath);
            return outputZipPath;
            
        } catch (IOException e) {
            throw new VideoProcessingException("Erro ao comprimir arquivos: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void compressFilesToStream(List<Path> files, OutputStream outputStream) {
        log.info("Comprimindo {} arquivos para stream", files.size());
        
        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            for (Path file : files) {
                if (!file.toFile().exists()) {
                    log.warn("Arquivo não encontrado, ignorando: {}", file);
                    continue;
                }
                
                addFileToZip(zos, file);
            }
            
            zos.finish();
            log.info("Compressão para stream concluída");
            
        } catch (IOException e) {
            throw new VideoProcessingException("Erro ao comprimir arquivos para stream: " + e.getMessage(), e);
        }
    }
    
    private void addFileToZip(ZipOutputStream zos, Path file) throws IOException {
        String fileName = file.getFileName().toString();
        log.debug("Adicionando arquivo ao ZIP: {}", fileName);
        
        ZipEntry zipEntry = new ZipEntry(fileName);
        zos.putNextEntry(zipEntry);
        
        try (FileInputStream fis = new FileInputStream(file.toFile())) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }
        }
        
        zos.closeEntry();
    }
}
