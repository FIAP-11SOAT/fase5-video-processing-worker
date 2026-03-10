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
import java.util.concurrent.atomic.AtomicInteger;

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
    // extractFrames: ffmpeg not installed (real adapter throws)
    // -----------------------------------------------------------------------

    @Test
    void extractFrames_deveLancarVideoProcessingException_quandoFFmpegNaoInstalado() throws IOException {
        Path fakeVideo = tempDir.resolve("video.mp4");
        Files.createFile(fakeVideo);
        Path outputDir = tempDir.resolve("frames");

        assertThatThrownBy(() -> adapter.extractFrames(fakeVideo, outputDir, 1))
                .isInstanceOf(VideoProcessingException.class);
    }

    // -----------------------------------------------------------------------
    // extractFrames: full success path via overridden runProcess
    // -----------------------------------------------------------------------

    @Test
    void extractFrames_deveRetornarFrames_quandoFFmpegExecutaComSucesso() throws Exception {
        Path outputDir = tempDir.resolve("frames");
        Files.createDirectories(outputDir);

        for (int i = 1; i <= 3; i++) {
            Path framePath = outputDir.resolve(String.format("frame_%04d.png", i));
            BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(img, "PNG", framePath.toFile());
        }

        FFmpegVideoProcessorAdapter testAdapter = new FFmpegVideoProcessorAdapter() {
            @Override
            protected int runProcess(String... command) { return 0; }
        };

        Path videoPath = tempDir.resolve("video.mp4");
        Files.createFile(videoPath);

        List<Frame> frames = testAdapter.extractFrames(videoPath, outputDir, 1);

        assertThat(frames).hasSize(3);
        assertThat(frames.get(0).getFileName()).isEqualTo("frame_0001.png");
        assertThat(frames.get(0).getFrameNumber()).isEqualTo(1);
        assertThat(frames.get(0).getSizeInBytes()).isPositive();
        assertThat(frames.get(0).getData()).isNotNull();
    }

    @Test
    void extractFrames_deveRetornarListaVazia_quandoNenhumPNGEncontrado() throws Exception {
        Path outputDir = tempDir.resolve("frames");
        Files.createDirectories(outputDir);

        FFmpegVideoProcessorAdapter testAdapter = new FFmpegVideoProcessorAdapter() {
            @Override
            protected int runProcess(String... command) { return 0; }
        };

        Path videoPath = tempDir.resolve("video.mp4");
        Files.createFile(videoPath);

        List<Frame> frames = testAdapter.extractFrames(videoPath, outputDir, 1);

        assertThat(frames).isEmpty();
    }

    @Test
    void extractFrames_deveIgnorarArquivosNaoPNG_quandoDirContemOutrosArquivos() throws Exception {
        Path outputDir = tempDir.resolve("frames");
        Files.createDirectories(outputDir);

        Path pngFile = outputDir.resolve("frame_0001.png");
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "PNG", pngFile.toFile());
        Files.write(outputDir.resolve("output.txt"), "not a frame".getBytes());

        FFmpegVideoProcessorAdapter testAdapter = new FFmpegVideoProcessorAdapter() {
            @Override
            protected int runProcess(String... command) { return 0; }
        };

        Path videoPath = tempDir.resolve("video.mp4");
        Files.createFile(videoPath);

        List<Frame> frames = testAdapter.extractFrames(videoPath, outputDir, 1);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).getFileName()).isEqualTo("frame_0001.png");
    }

    // -----------------------------------------------------------------------
    // extractFrames: FFmpeg extraction fails (exit code != 0)
    // -----------------------------------------------------------------------

    @Test
    void extractFrames_deveLancarExcecao_quandoFFmpegExtractionRetornaCodigo1() throws IOException {
        Path outputDir = tempDir.resolve("frames");
        Path videoPath = tempDir.resolve("video.mp4");
        Files.createFile(videoPath);

        AtomicInteger callCount = new AtomicInteger(0);
        FFmpegVideoProcessorAdapter testAdapter = new FFmpegVideoProcessorAdapter() {
            @Override
            protected int runProcess(String... command) {
                return callCount.incrementAndGet() == 1 ? 0 : 1;
            }
        };

        assertThatThrownBy(() -> testAdapter.extractFrames(videoPath, outputDir, 1))
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("código de saída");
    }

    // -----------------------------------------------------------------------
    // validateFFmpegInstalled: exit code != 0
    // -----------------------------------------------------------------------

    @Test
    void extractFrames_deveLancarExcecao_quandoValidacaoFFmpegRetornaCodigo1() throws IOException {
        Path outputDir = tempDir.resolve("frames");
        Path videoPath = tempDir.resolve("video.mp4");
        Files.createFile(videoPath);

        FFmpegVideoProcessorAdapter testAdapter = new FFmpegVideoProcessorAdapter() {
            @Override
            protected int runProcess(String... command) { return 1; }
        };

        assertThatThrownBy(() -> testAdapter.extractFrames(videoPath, outputDir, 1))
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("FFmpeg não está instalado");
    }

    // -----------------------------------------------------------------------
    // validateFFmpegInstalled: IOException
    // -----------------------------------------------------------------------

    @Test
    void extractFrames_deveLancarExcecao_quandoIOExceptionNaValidacao() throws IOException {
        Path outputDir = tempDir.resolve("frames");
        Path videoPath = tempDir.resolve("video.mp4");
        Files.createFile(videoPath);

        FFmpegVideoProcessorAdapter testAdapter = new FFmpegVideoProcessorAdapter() {
            @Override
            protected int runProcess(String... command) throws IOException {
                throw new IOException("ffmpeg not found");
            }
        };

        assertThatThrownBy(() -> testAdapter.extractFrames(videoPath, outputDir, 1))
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("Erro ao verificar instalação do FFmpeg");
    }

    // -----------------------------------------------------------------------
    // validateFFmpegInstalled: InterruptedException
    // -----------------------------------------------------------------------

    @Test
    void extractFrames_deveLancarExcecao_quandoInterrompidoNaValidacao() throws IOException {
        Path outputDir = tempDir.resolve("frames");
        Path videoPath = tempDir.resolve("video.mp4");
        Files.createFile(videoPath);

        FFmpegVideoProcessorAdapter testAdapter = new FFmpegVideoProcessorAdapter() {
            @Override
            protected int runProcess(String... command) throws InterruptedException {
                throw new InterruptedException("interrupted");
            }
        };

        assertThatThrownBy(() -> testAdapter.extractFrames(videoPath, outputDir, 1))
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("Verificação do FFmpeg foi interrompida");

        Thread.interrupted(); // Clear interrupt flag
    }

    // -----------------------------------------------------------------------
    // extractFrames: IOException during extraction (after validation succeeds)
    // -----------------------------------------------------------------------

    @Test
    void extractFrames_deveLancarExcecao_quandoIOExceptionDuranteExtracao() throws IOException {
        Path outputDir = tempDir.resolve("frames");
        Path videoPath = tempDir.resolve("video.mp4");
        Files.createFile(videoPath);

        AtomicInteger callCount = new AtomicInteger(0);
        FFmpegVideoProcessorAdapter testAdapter = new FFmpegVideoProcessorAdapter() {
            @Override
            protected int runProcess(String... command) throws IOException {
                if (callCount.incrementAndGet() == 1) return 0;
                throw new IOException("disk error");
            }
        };

        assertThatThrownBy(() -> testAdapter.extractFrames(videoPath, outputDir, 1))
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("Erro ao executar FFmpeg");
    }

    // -----------------------------------------------------------------------
    // extractFrames: InterruptedException during extraction
    // -----------------------------------------------------------------------

    @Test
    void extractFrames_deveLancarExcecao_quandoInterrompidoDuranteExtracao() throws IOException {
        Path outputDir = tempDir.resolve("frames");
        Path videoPath = tempDir.resolve("video.mp4");
        Files.createFile(videoPath);

        AtomicInteger callCount = new AtomicInteger(0);
        FFmpegVideoProcessorAdapter testAdapter = new FFmpegVideoProcessorAdapter() {
            @Override
            protected int runProcess(String... command) throws InterruptedException {
                if (callCount.incrementAndGet() == 1) return 0;
                throw new InterruptedException("interrupted");
            }
        };

        assertThatThrownBy(() -> testAdapter.extractFrames(videoPath, outputDir, 1))
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("Processamento interrompido");

        Thread.interrupted(); // Clear flag
    }

    // -----------------------------------------------------------------------
    // extractFrameNumber via reflection
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
}
