package com.fiap.videoprocessor.domain;

import com.fiap.videoprocessor.domain.model.Frame;
import com.fiap.videoprocessor.domain.model.ProcessingResult;
import com.fiap.videoprocessor.domain.model.StatusEnum;
import com.fiap.videoprocessor.domain.model.Video;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainModelTest {

    @Test
    void statusEnum_deveConterTodosOsValores() {
        assertThat(StatusEnum.values()).hasSize(3);
        assertThat(StatusEnum.UPLOADED.getValue()).isEqualTo("uploaded");
        assertThat(StatusEnum.PROCESSED.getValue()).isEqualTo("processed");
        assertThat(StatusEnum.ERROR_PROCESSING.getValue()).isEqualTo("error-processing");
    }

    @Test
    void statusEnum_deveConterDescricoes() {
        assertThat(StatusEnum.UPLOADED.getDescription()).isNotBlank();
        assertThat(StatusEnum.PROCESSED.getDescription()).isNotBlank();
        assertThat(StatusEnum.ERROR_PROCESSING.getDescription()).isNotBlank();
    }

    @Test
    void frame_deveCriarComBuilder() {
        Frame frame = Frame.builder()
                .fileName("frame_001.png")
                .frameNumber(1)
                .sizeInBytes(2048)
                .s3Key("user/video/frame_001.png")
                .build();

        assertThat(frame.getFileName()).isEqualTo("frame_001.png");
        assertThat(frame.getFrameNumber()).isEqualTo(1);
        assertThat(frame.getSizeInBytes()).isEqualTo(2048);
        assertThat(frame.getS3Key()).isEqualTo("user/video/frame_001.png");
    }

    @Test
    void processingResult_deveCriarComBuilder() {
        Frame frame = Frame.builder().fileName("f.png").frameNumber(1).sizeInBytes(100).build();

        ProcessingResult result = ProcessingResult.builder()
                .videoId("vid123")
                .success(true)
                .message("Concluído com 1 frame")
                .frameCount(1)
                .frames(List.of(frame))
                .zipS3Key("user/vid123.zip")
                .processingTimeMs(1500L)
                .build();

        assertThat(result.getVideoId()).isEqualTo("vid123");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFrameCount()).isEqualTo(1);
        assertThat(result.getZipS3Key()).isEqualTo("user/vid123.zip");
        assertThat(result.getProcessingTimeMs()).isEqualTo(1500L);
    }

    @Test
    void video_deveAtualizarStatusViaMetodosHelpers() {
        Video video = Video.builder()
                .id("vid1")
                .s3Key("user/vid1.mp4")
                .bucket("test-bucket")
                .status(Video.ProcessingStatus.PENDING)
                .build();

        Video downloading = video.markAsDownloading();
        Video processing  = video.markAsProcessing();
        Video completed   = video.markAsCompleted();
        Video failed      = video.markAsFailed();

        assertThat(downloading.getStatus()).isEqualTo(Video.ProcessingStatus.DOWNLOADING);
        assertThat(processing.getStatus()).isEqualTo(Video.ProcessingStatus.PROCESSING);
        assertThat(completed.getStatus()).isEqualTo(Video.ProcessingStatus.COMPLETED);
        assertThat(failed.getStatus()).isEqualTo(Video.ProcessingStatus.FAILED);
    }

    @Test
    void video_deveManterDadosOriginaisComWith() {
        Video original = Video.builder()
                .id("vid1")
                .s3Key("user/vid1.mp4")
                .bucket("test-bucket")
                .status(Video.ProcessingStatus.PENDING)
                .build();

        Video modified = original.markAsCompleted();

        // original should be unchanged
        assertThat(original.getStatus()).isEqualTo(Video.ProcessingStatus.PENDING);
        assertThat(modified.getId()).isEqualTo("vid1");
        assertThat(modified.getS3Key()).isEqualTo("user/vid1.mp4");
    }
}
