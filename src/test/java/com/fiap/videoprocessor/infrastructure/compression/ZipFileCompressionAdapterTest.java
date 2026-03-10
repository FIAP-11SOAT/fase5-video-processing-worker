package com.fiap.videoprocessor.infrastructure.compression;

import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.infrastructure.adapter.compression.ZipFileCompressionAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipFileCompressionAdapterTest {

    private final ZipFileCompressionAdapter adapter = new ZipFileCompressionAdapter();

    @Test
    void compressFiles_deveCriarZipComArquivosValidos(@TempDir Path tempDir) throws IOException {
        // Arrange
        Path file1 = tempDir.resolve("frame_001.png");
        Path file2 = tempDir.resolve("frame_002.png");
        Files.writeString(file1, "conteudo frame 1");
        Files.writeString(file2, "conteudo frame 2");

        Path outputZip = tempDir.resolve("frames.zip");

        // Act
        Path result = adapter.compressFiles(List.of(file1, file2), outputZip);

        // Assert
        assertThat(result).isEqualTo(outputZip);
        assertThat(Files.exists(outputZip)).isTrue();
        assertThat(Files.size(outputZip)).isGreaterThan(0);

        // Verify zip content
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(outputZip))) {
            int entryCount = 0;
            while (zis.getNextEntry() != null) entryCount++;
            assertThat(entryCount).isEqualTo(2);
        }
    }

    @Test
    void compressFiles_deveIgnorarArquivosInexistentes(@TempDir Path tempDir) throws IOException {
        // Arrange
        Path existingFile = tempDir.resolve("frame_001.png");
        Files.writeString(existingFile, "conteudo valido");
        Path missingFile = tempDir.resolve("nao_existe.png");

        Path outputZip = tempDir.resolve("frames.zip");

        // Act
        Path result = adapter.compressFiles(List.of(existingFile, missingFile), outputZip);

        // Assert
        assertThat(result).isNotNull();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(outputZip))) {
            int entryCount = 0;
            while (zis.getNextEntry() != null) entryCount++;
            assertThat(entryCount).isEqualTo(1);
        }
    }

    @Test
    void compressFilesToStream_deveCriarZipNoStream(@TempDir Path tempDir) throws IOException {
        // Arrange
        Path file1 = tempDir.resolve("frame_001.png");
        Files.writeString(file1, "conteudo para stream");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Act
        adapter.compressFilesToStream(List.of(file1), baos);

        // Assert
        assertThat(baos.size()).isGreaterThan(0);

        try (ZipInputStream zis = new ZipInputStream(
                new java.io.ByteArrayInputStream(baos.toByteArray()))) {
            assertThat(zis.getNextEntry()).isNotNull();
        }
    }

    @Test
    void compressFiles_deveLancarExcecaoQuandoOutputPathInvalido() {
        Path invalidPath = Path.of("/caminho/invalido/que/nao/existe/output.zip");

        assertThatThrownBy(() -> adapter.compressFiles(List.of(), invalidPath))
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("Erro ao comprimir");
    }
}
