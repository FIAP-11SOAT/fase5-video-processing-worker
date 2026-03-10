package com.fiap.videoprocessor.infrastructure.adapter.processor;

import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.domain.model.Frame;
import com.fiap.videoprocessor.domain.ports.output.VideoProcessorPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Adapter de infraestrutura para processamento de vídeo com FFmpeg
 */
@Component
@Slf4j
@SuppressWarnings("java:S4721") // FFmpeg command execution is intentional and input paths are internally controlled
public class FFmpegVideoProcessorAdapter implements VideoProcessorPort {

    private static final String FRAME_PATTERN = "frame_%04d.png";

    @Override
    public List<Frame> extractFrames(Path videoPath, Path outputDir, int framesPerSecond) {
        log.info("Extraindo frames: video={}, fps={}, output={}",
                videoPath, framesPerSecond, outputDir);

        validateFFmpegInstalled();

        try {
            Files.createDirectories(outputDir);

            String framePattern = outputDir.resolve(FRAME_PATTERN).toString();

            int exitCode = runProcess(
                    "ffmpeg",
                    "-i", videoPath.toString(),
                    "-vf", String.format("fps=%d", framesPerSecond),
                    "-y",
                    framePattern
            );

            if (exitCode != 0) {
                throw new VideoProcessingException(
                        String.format("FFmpeg falhou com código de saída: %d", exitCode));
            }

            // Listar frames extraídos
            List<Frame> frames = new ArrayList<>();
            try (Stream<Path> paths = Files.list(outputDir)) {
                paths.filter(path -> path.toString().endsWith(".png"))
                        .sorted()
                        .forEach(framePath -> {
                            try {
                                byte[] data = Files.readAllBytes(framePath);
                                int frameNumber = extractFrameNumber(framePath.getFileName().toString());

                                Frame frame = Frame.builder()
                                        .fileName(framePath.getFileName().toString())
                                        .frameNumber(frameNumber)
                                        .data(data)
                                        .sizeInBytes(data.length)
                                        .build();

                                frames.add(frame);
                            } catch (IOException e) {
                                log.warn("Erro ao ler frame: {}", framePath, e);
                            }
                        });
            }

            log.info("Frames extraídos com sucesso: {} frames", frames.size());
            return frames;

        } catch (IOException e) {
            throw new VideoProcessingException("Erro ao executar FFmpeg", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VideoProcessingException("Processamento interrompido", e);
        }
    }

    private void validateFFmpegInstalled() {
        try {
            int exitCode = runProcess("ffmpeg", "-version");
            if (exitCode != 0) {
                throw new VideoProcessingException("FFmpeg não está instalado ou não está no PATH");
            }
        } catch (IOException e) {
            throw new VideoProcessingException("Erro ao verificar instalação do FFmpeg: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VideoProcessingException("Verificação do FFmpeg foi interrompida: " + e.getMessage(), e);
        }
    }

    /**
     * Executes a process and returns its exit code. Protected to allow overriding in tests.
     */
    protected int runProcess(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        logFFmpegOutput(process);
        try {
            return process.waitFor();
        } finally {
            process.destroyForcibly();
        }
    }

    private void logFFmpegOutput(Process process) {
        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg: {}", line);
                }
            } catch (IOException e) {
                log.warn("Erro ao ler output do FFmpeg", e);
            }
        });
        readerThread.setName("ffmpeg-output-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private int extractFrameNumber(String fileName) {
        // Extrai número do frame de nomes como "frame_0001.png"
        try {
            String number = fileName.replaceAll("[^0-9]", "");
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
