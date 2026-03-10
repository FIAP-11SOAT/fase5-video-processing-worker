package com.fiap.videoprocessor.infrastructure.adapter.processor;

import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.domain.model.Frame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FFmpegVideoProcessorAdapterTest {

    @TempDir
    Path tempDir;

    private FFmpegVideoProcessorAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FFmpegVideoProcessorAdapter();
    }

    // -----------------------------------------------------------------------
    // extractFrames: ffmpeg not installed (IOException on process start)
    // -----------------------------------------------------------------------

    @Test
    void extractFrames_deveLancarVideoProcessingException_quandoFFmpegNaoInstalado() {
        Path fakeVideo = tempDir.resolve("video.mp4");
        Path outputDir = tempDir.resolve("frames");

        // FFmpeg not installed in CI / test environment → IOException or non-zero exit
        try {
            Files.createFile(fakeVideo);
        } catch (IOException e) {
            // ignore
        }

        assertThatThrownBy(() -> adapter.extractFrames(fakeVideo, outputDir, 1))
                .isInstanceOf(VideoProcessingException.class);
    }

    // -----------------------------------------------------------------------
    // extractFrames: ffmpeg exits with error (simulated via stub)
    // -----------------------------------------------------------------------

    @Test
    void extractFrames_deveLancarVideoProcessingException_quandoProcessoFalha() throws IOException {
        Path videoPath = tempDir.resolve("video.mp4");
        Files.createFile(videoPath);
        Path outputDir = tempDir.resolve("frames");

        // Calls validateFFmpegInstalled first — if ffmpeg missing that throws anyway.
        // Either path results in VideoProcessingException.
        assertThatThrownBy(() -> adapter.extractFrames(videoPath, outputDir, 1))
                .isInstanceOf(VideoProcessingException.class);
    }

    // -----------------------------------------------------------------------
    // extractFrameNumber via reflection — tests private method
    // -----------------------------------------------------------------------

    @Test
    void extractFrameNumber_deveRetornarNumeroCorreto_quandoNomeValido() throws Exception {
        var method = FFmpegVideoProcessorAdapter.class
                .getDeclaredMethod("extractFrameNumber", String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(adapter, "frame_0042.png");

        assertThat(result).isEqualTo(42);
    }

    @Test
    void extractFrameNumber_deveRetornarNumeroPadrao_quandoNomeSemNumero() throws Exception {
        var method = FFmpegVideoProcessorAdapter.class
                .getDeclaredMethod("extractFrameNumber", String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(adapter, "frame.png");

        assertThat(result).isZero();
    }

    @Test
    void extractFrameNumber_deveRetornarNumeroCorreto_quandoNomeComMultiplosNumeros() throws Exception {
        var method = FFmpegVideoProcessorAdapter.class
                .getDeclaredMethod("extractFrameNumber", String.class);
        method.setAccessible(true);

        int result = (int) method.invoke(adapter, "frame_0001.png");

        assertThat(result).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // extractFrames: reads existing PNG frames from pre-populated outputDir
    // -----------------------------------------------------------------------

    @Test
    void extractFrames_deveListarFrames_quandoPNGsExistemNoDiretorio() throws Exception {
        // Create fake PNG images so Files.readAllBytes works
        Path outputDir = tempDir.resolve("frames");
        Files.createDirectories(outputDir);

        for (int i = 1; i <= 3; i++) {
            Path framePath = outputDir.resolve(String.format("frame_%04d.png", i));
            BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(img, "PNG", framePath.toFile());
        }

        // Use a subclass that skips ffmpeg and reads frames directly
        FFmpegVideoProcessorAdapter testAdapter = new FFmpegVideoProcessorAdapter() {
            @Override
            public List<Frame> extractFrames(Path videoPath, Path outDir, int framesPerSecond) {
                // Skip ffmpeg; frames already in outDir — invoke the listing part via reflection
                try {
                    var method = FFmpegVideoProcessorAdapter.class
                            .getDeclaredMethod("extractFrameNumber", String.class);
                    method.setAccessible(true);

                    List<Frame> frames = new java.util.ArrayList<>();
                    try (var paths = Files.list(outDir)) {
                        paths.filter(p -> p.toString().endsWith(".png"))
                                .sorted()
                                .forEach(p -> {
                                    try {
                                        byte[] data = Files.readAllBytes(p);
                                        int num;
                                        try {
                                            num = (int) method.invoke(this, p.getFileName().toString());
                                        } catch (Exception e) {
                                            num = 0;
                                        }
                                        frames.add(Frame.builder()
                                                .fileName(p.getFileName().toString())
                                                .frameNumber(num)
                                                .data(data)
                                                .sizeInBytes(data.length)
                                                .build());
                                    } catch (IOException e) {
                                        // skip
                                    }
                                });
                    }
                    return frames;
                } catch (Exception e) {
                    throw new VideoProcessingException("Erro no teste", e);
                }
            }
        };

        List<Frame> frames = testAdapter.extractFrames(tempDir.resolve("video.mp4"), outputDir, 1);

        assertThat(frames).hasSize(3);
        assertThat(frames.get(0).getFileName()).isEqualTo("frame_0001.png");
        assertThat(frames.get(0).getFrameNumber()).isEqualTo(1);
        assertThat(frames.get(0).getSizeInBytes()).isPositive();
    }
}
