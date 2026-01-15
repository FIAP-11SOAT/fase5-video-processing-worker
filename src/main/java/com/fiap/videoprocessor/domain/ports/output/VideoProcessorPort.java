package com.fiap.videoprocessor.domain.ports.output;

import com.fiap.videoprocessor.domain.model.Frame;

import java.nio.file.Path;
import java.util.List;

/**
 * Porta de saída para processamento de vídeos (FFmpeg)
 */
public interface VideoProcessorPort {
    /**
     * Extrai frames de um vídeo
     * 
     * @param videoPath Caminho do arquivo de vídeo
     * @param outputDir Diretório de saída para os frames
     * @param framesPerSecond Quantidade de frames por segundo a serem extraídos
     * @return Lista de frames extraídos
     */
    List<Frame> extractFrames(Path videoPath, Path outputDir, int framesPerSecond);
}
