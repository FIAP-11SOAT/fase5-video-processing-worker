package com.fiap.videoprocessor.domain.ports.output;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Porta de saída para compressão de arquivos
 */
public interface FileCompressionPort {
    /**
     * Comprime uma lista de arquivos em um arquivo ZIP
     * 
     * @param files Lista de arquivos a serem comprimidos
     * @param outputZipPath Caminho do arquivo ZIP de saída
     * @return Caminho do arquivo ZIP criado
     */
    Path compressFiles(List<Path> files, Path outputZipPath);
    
    /**
     * Comprime uma lista de arquivos diretamente para um OutputStream
     * 
     * @param files Lista de arquivos a serem comprimidos
     * @param outputStream OutputStream para escrever o ZIP
     */
    void compressFilesToStream(List<Path> files, OutputStream outputStream);
}
