package com.fiap.videoprocessor.service;

import com.fiap.videoprocessor.application.service.ProcessVideoService;
import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.domain.model.Frame;
import com.fiap.videoprocessor.domain.model.ProcessingResult;
import com.fiap.videoprocessor.domain.model.StatusEnum;
import com.fiap.videoprocessor.domain.ports.output.*;
import com.fiap.videoprocessor.infrastructure.messaging.model.VideoProcessingMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessVideoServiceTest {

    @Mock
    private VideoStoragePort videoStoragePort;

    @Mock
    private VideoProcessorPort videoProcessorPort;

    @Mock
    private FileCompressionPort fileCompressionPort;

    @Mock
    private VideoStatusPort videoStatusPort;

    @Mock
    private NotificationPort notificationPort;

    @InjectMocks
    private ProcessVideoService processVideoService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processVideoService, "framesPerSecond", 1);
        ReflectionTestUtils.setField(processVideoService, "tempDir", "./temp-test");
        ReflectionTestUtils.setField(processVideoService, "defaultOutputBucket", "test-output-bucket");
    }

    /**
     * Mock compressFiles so it creates an actual (empty) file at the output path,
     * because ProcessVideoService calls Files.size(zipPath) right after compression.
     */
    private void mockCompressFilesCreatingFile() throws Exception {
        doAnswer(invocation -> {
            Path outputZip = invocation.getArgument(1);
            Files.createDirectories(outputZip.getParent());
            Files.write(outputZip, "fake-zip".getBytes());
            return outputZip;
        }).when(fileCompressionPort).compressFiles(any(), any(Path.class));
    }

    @Test
    void processVideo_deveProcessarComSucesso() throws Exception {
        // Arrange
        VideoProcessingMessage message = VideoProcessingMessage.builder()
                .bucket("test-bucket")
                .key("user123/video456.mp4")
                .userId("user123")
                .videoId("video456")
                .videoName("video456")
                .build();

        Frame frame = Frame.builder()
                .fileName("frame_001.png")
                .frameNumber(1)
                .sizeInBytes(1024)
                .build();

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenReturn(List.of(frame));
        when(videoStoragePort.uploadFile(anyString(), anyString(), any(Path.class)))
                .thenReturn("user123/video456.zip");
        mockCompressFilesCreatingFile();

        // Act
        processVideoService.processVideo(message);

        // Assert
        verify(videoStatusPort).updateStatusToProcessing("user123/video456.mp4", "user123", "video456");
        verify(videoStatusPort).updateStatusToSuccess(eq("user123/video456.mp4"), anyString());
        verify(notificationPort).sendNotification(
                eq("user123/video456.mp4"), eq("video456"), eq("user123"), eq(StatusEnum.PROCESSED));
    }

    @Test
    void processVideo_deveAtualizarStatusParaErroQuandoRuntimeExceptionFalhar() {
        // Arrange
        VideoProcessingMessage message = VideoProcessingMessage.builder()
                .bucket("test-bucket")
                .key("user123/video456.mp4")
                .userId("user123")
                .videoId("video456")
                .videoName("video456")
                .build();

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenThrow(new RuntimeException("FFmpeg error"));

        // Act & Assert
        assertThatThrownBy(() -> processVideoService.processVideo(message))
                .isInstanceOf(VideoProcessingException.class);

        verify(videoStatusPort).updateStatusToError(eq("user123/video456.mp4"), anyString());
        verify(notificationPort).sendNotification(
                eq("user123/video456.mp4"), eq("video456"), eq("user123"), eq(StatusEnum.ERROR_PROCESSING));
    }

    @Test
    void processVideo_deveAtualizarStatusParaErroQuandoVPELancada() {
        // Arrange — extractFrames throws VideoProcessingException directly
        VideoProcessingMessage message = VideoProcessingMessage.builder()
                .bucket("test-bucket")
                .key("user123/video456.mp4")
                .userId("user123")
                .videoId("video456")
                .videoName("video456")
                .build();

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenThrow(new VideoProcessingException("VPE error"));

        // Act & Assert
        assertThatThrownBy(() -> processVideoService.processVideo(message))
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("VPE error");

        verify(videoStatusPort).updateStatusToError(eq("user123/video456.mp4"), anyString());
        verify(notificationPort).sendNotification(
                eq("user123/video456.mp4"), eq("video456"), eq("user123"), eq(StatusEnum.ERROR_PROCESSING));
    }

    @Test
    void processVideo_deveRelancaVPE_quandoDBUpdateFalhaDentroDoHandlerVPE() {
        // Arrange — VPE thrown from process(), then updateStatusToError also throws
        VideoProcessingMessage message = VideoProcessingMessage.builder()
                .bucket("test-bucket")
                .key("user123/video456.mp4")
                .userId("user123")
                .videoId("video456")
                .videoName("video456")
                .build();

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenThrow(new VideoProcessingException("original VPE"));
        doThrow(new RuntimeException("DB failure"))
                .when(videoStatusPort).updateStatusToError(anyString(), anyString());

        // Should still re-throw the original VPE
        assertThatThrownBy(() -> processVideoService.processVideo(message))
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("original VPE");
    }

    @Test
    void processVideo_deveRelancaVPE_quandoDBUpdateFalhaDentroDoHandlerRuntime() {
        // Arrange — RuntimeException thrown from process(), then updateStatusToError also throws
        VideoProcessingMessage message = VideoProcessingMessage.builder()
                .bucket("test-bucket")
                .key("user123/video456.mp4")
                .userId("user123")
                .videoId("video456")
                .videoName("video456")
                .build();

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenThrow(new RuntimeException("runtime error"));
        doThrow(new RuntimeException("DB failure"))
                .when(videoStatusPort).updateStatusToError(anyString(), anyString());

        // Should wrap original in VideoProcessingException
        assertThatThrownBy(() -> processVideoService.processVideo(message))
                .isInstanceOf(VideoProcessingException.class);
    }

    @Test
    void process_deveLancarExcecaoQuandoNenhumFrameExtraido() {
        // Arrange
        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenReturn(List.of());

        // Act & Assert
        assertThatThrownBy(() ->
                processVideoService.process("video123", "user/video.mp4", "input-bucket", "output-bucket"))
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("Nenhum frame foi extraído");
    }

    @Test
    void process_deveRetornarResultadoComQuantidadeCorretaDeFrames() throws Exception {
        // Arrange
        List<Frame> frames = List.of(
                Frame.builder().fileName("frame_001.png").frameNumber(1).sizeInBytes(1024).build(),
                Frame.builder().fileName("frame_002.png").frameNumber(2).sizeInBytes(1024).build(),
                Frame.builder().fileName("frame_003.png").frameNumber(3).sizeInBytes(1024).build()
        );

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenReturn(frames);
        when(videoStoragePort.uploadFile(anyString(), anyString(), any(Path.class)))
                .thenReturn("user/video.zip");
        mockCompressFilesCreatingFile();

        // Act
        ProcessingResult result = processVideoService.process(
                "video123", "user/video.mp4", "input-bucket", "output-bucket");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFrameCount()).isEqualTo(3);
        assertThat(result.getVideoId()).isEqualTo("video123");
        assertThat(result.getZipS3Key()).isEqualTo("user/video.zip");
    }

    @Test
    void process_deveGerarZipKeyCorretamenteSemSubdiretorio() throws Exception {
        // Arrange
        Frame frame = Frame.builder().fileName("frame_001.png").frameNumber(1).sizeInBytes(512).build();

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenReturn(List.of(frame));
        when(videoStoragePort.uploadFile(anyString(), anyString(), any(Path.class)))
                .thenReturn("myvideo.zip");
        mockCompressFilesCreatingFile();

        // Act
        ProcessingResult result = processVideoService.process(
                "vid1", "myvideo.mp4", "input-bucket", "output-bucket");

        // Assert
        assertThat(result.getZipS3Key()).isEqualTo("myvideo.zip");
    }

    @Test
    void process_deveLancarVPE_quandoArquivoZipNaoExisteAposCompressao() {
        // compressFiles mock returns null and does NOT create the file,
        // so Files.size(zipPath) throws NoSuchFileException (IOException) → wraps to VPE
        Frame frame = Frame.builder().fileName("frame_001.png").frameNumber(1).sizeInBytes(1024).build();

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenReturn(List.of(frame));
        // Do NOT call mockCompressFilesCreatingFile() — zipPath will not exist

        assertThatThrownBy(() ->
                processVideoService.process("video123", "user/video.mp4", "input-bucket", "output-bucket"))
                .isInstanceOf(VideoProcessingException.class);
    }

    @Test
    void process_deveLancarVPE_quandoUploadFalha() throws Exception {
        // Arrange
        Frame frame = Frame.builder().fileName("frame_001.png").frameNumber(1).sizeInBytes(1024).build();

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenReturn(List.of(frame));
        mockCompressFilesCreatingFile();
        when(videoStoragePort.uploadFile(anyString(), anyString(), any(Path.class)))
                .thenThrow(new VideoProcessingException("S3 upload failed"));

        // Act & Assert
        assertThatThrownBy(() ->
                processVideoService.process("video123", "user/video.mp4", "input-bucket", "output-bucket"))
                .isInstanceOf(VideoProcessingException.class);
    }

    @Test
    void process_deveGerarZipKeySemExtensao_quandoS3KeySemExtensao() throws Exception {
        // Arrange: s3Key without extension
        Frame frame = Frame.builder().fileName("frame_001.png").frameNumber(1).sizeInBytes(512).build();

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenReturn(List.of(frame));
        when(videoStoragePort.uploadFile(anyString(), anyString(), any(Path.class)))
                .thenReturn("user/videonoext.zip");
        mockCompressFilesCreatingFile();

        ProcessingResult result = processVideoService.process(
                "vid1", "user/videonoext", "input-bucket", "output-bucket");

        assertThat(result.getZipS3Key()).isEqualTo("user/videonoext.zip");
    }
}


@ExtendWith(MockitoExtension.class)
class ProcessVideoServiceTest {

    @Mock
    private VideoStoragePort videoStoragePort;

    @Mock
    private VideoProcessorPort videoProcessorPort;

    @Mock
    private FileCompressionPort fileCompressionPort;

    @Mock
    private VideoStatusPort videoStatusPort;

    @Mock
    private NotificationPort notificationPort;

    @InjectMocks
    private ProcessVideoService processVideoService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(processVideoService, "framesPerSecond", 1);
        ReflectionTestUtils.setField(processVideoService, "tempDir", "./temp-test");
        ReflectionTestUtils.setField(processVideoService, "defaultOutputBucket", "test-output-bucket");
    }

    /**
     * Mock compressFiles so it creates an actual (empty) file at the output path,
     * because ProcessVideoService calls Files.size(zipPath) right after compression.
     */
    private void mockCompressFilesCreatingFile() throws Exception {
        doAnswer(invocation -> {
            Path outputZip = invocation.getArgument(1);
            Files.createDirectories(outputZip.getParent());
            Files.write(outputZip, "fake-zip".getBytes());
            return outputZip;
        }).when(fileCompressionPort).compressFiles(any(), any(Path.class));
    }

    @Test
    void processVideo_deveProcessarComSucesso() throws Exception {
        // Arrange
        VideoProcessingMessage message = VideoProcessingMessage.builder()
                .bucket("test-bucket")
                .key("user123/video456.mp4")
                .userId("user123")
                .videoId("video456")
                .videoName("video456")
                .build();

        Frame frame = Frame.builder()
                .fileName("frame_001.png")
                .frameNumber(1)
                .sizeInBytes(1024)
                .build();

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenReturn(List.of(frame));
        when(videoStoragePort.uploadFile(anyString(), anyString(), any(Path.class)))
                .thenReturn("user123/video456.zip");
        mockCompressFilesCreatingFile();

        // Act
        processVideoService.processVideo(message);

        // Assert
        verify(videoStatusPort).updateStatusToProcessing("user123/video456.mp4", "user123", "video456");
        verify(videoStatusPort).updateStatusToSuccess(eq("user123/video456.mp4"), anyString());
        verify(notificationPort).sendNotification(
                eq("user123/video456.mp4"), eq("video456"), eq("user123"), eq(StatusEnum.PROCESSED));
    }

    @Test
    void processVideo_deveAtualizarStatusParaErroQuandoFalhar() {
        // Arrange
        VideoProcessingMessage message = VideoProcessingMessage.builder()
                .bucket("test-bucket")
                .key("user123/video456.mp4")
                .userId("user123")
                .videoId("video456")
                .videoName("video456")
                .build();

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenThrow(new RuntimeException("FFmpeg error"));

        // Act & Assert
        assertThatThrownBy(() -> processVideoService.processVideo(message))
                .isInstanceOf(VideoProcessingException.class);

        verify(videoStatusPort).updateStatusToError(eq("user123/video456.mp4"), anyString());
        verify(notificationPort).sendNotification(
                eq("user123/video456.mp4"), eq("video456"), eq("user123"), eq(StatusEnum.ERROR_PROCESSING));
    }

    @Test
    void process_deveLancarExcecaoQuandoNenhumFrameExtraido() {
        // Arrange
        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenReturn(List.of());

        // Act & Assert
        assertThatThrownBy(() ->
                processVideoService.process("video123", "user/video.mp4", "input-bucket", "output-bucket"))
                .isInstanceOf(VideoProcessingException.class)
                .hasMessageContaining("Nenhum frame foi extraído");
    }

    @Test
    void process_deveRetornarResultadoComQuantidadeCorretaDeFrames() throws Exception {
        // Arrange
        List<Frame> frames = List.of(
                Frame.builder().fileName("frame_001.png").frameNumber(1).sizeInBytes(1024).build(),
                Frame.builder().fileName("frame_002.png").frameNumber(2).sizeInBytes(1024).build(),
                Frame.builder().fileName("frame_003.png").frameNumber(3).sizeInBytes(1024).build()
        );

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenReturn(frames);
        when(videoStoragePort.uploadFile(anyString(), anyString(), any(Path.class)))
                .thenReturn("user/video.zip");
        mockCompressFilesCreatingFile();

        // Act
        ProcessingResult result = processVideoService.process(
                "video123", "user/video.mp4", "input-bucket", "output-bucket");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFrameCount()).isEqualTo(3);
        assertThat(result.getVideoId()).isEqualTo("video123");
        assertThat(result.getZipS3Key()).isEqualTo("user/video.zip");
    }

    @Test
    void process_deveGerarZipKeyCorretamenteSemSubdiretorio() throws Exception {
        // Arrange
        Frame frame = Frame.builder().fileName("frame_001.png").frameNumber(1).sizeInBytes(512).build();

        when(videoProcessorPort.extractFrames(any(Path.class), any(Path.class), anyInt()))
                .thenReturn(List.of(frame));
        when(videoStoragePort.uploadFile(anyString(), anyString(), any(Path.class)))
                .thenReturn("myvideo.zip");
        mockCompressFilesCreatingFile();

        // Act
        ProcessingResult result = processVideoService.process(
                "vid1", "myvideo.mp4", "input-bucket", "output-bucket");

        // Assert
        assertThat(result.getZipS3Key()).isEqualTo("myvideo.zip");
    }
}
