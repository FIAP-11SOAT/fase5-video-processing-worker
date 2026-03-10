package com.fiap.videoprocessor.infrastructure.storage;

import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.infrastructure.adapter.storage.S3StorageAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3StorageAdapterTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3StorageAdapter s3StorageAdapter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3StorageAdapter, "multipartPartSize", 10485760L);
    }

    // --- downloadVideo ---

    @Test
    void downloadVideo_deveRetornarPath_quandoSucesso(@TempDir Path dir) throws IOException {
        Path destPath = dir.resolve("video.mp4");
        byte[] content = "video content".getBytes();

        ResponseInputStream<GetObjectResponse> responseStream =
                new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        new ByteArrayInputStream(content));

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        Path result = s3StorageAdapter.downloadVideo("my-bucket", "user/video.mp4", destPath);

        assertThat(result).isEqualTo(destPath);
        assertThat(Files.readAllBytes(destPath)).isEqualTo(content);
    }

    @Test
    void downloadVideo_deveLancarExcecao_quandoS3Falha() {
        S3Exception s3Ex = (S3Exception) S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("not found").build())
                .build();
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(s3Ex);

        assertThatThrownBy(() ->
                s3StorageAdapter.downloadVideo("my-bucket", "user/video.mp4", tempDir.resolve("v.mp4")))
                .isInstanceOf(VideoProcessingException.class);
    }

    // --- downloadVideoAsStream ---

    @Test
    void downloadVideoAsStream_deveRetornarStream_quandoSucesso() {
        ResponseInputStream<GetObjectResponse> responseStream =
                new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        new ByteArrayInputStream("data".getBytes()));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        InputStream result = s3StorageAdapter.downloadVideoAsStream("my-bucket", "user/video.mp4");

        assertThat(result).isNotNull();
    }

    @Test
    void downloadVideoAsStream_deveLancarExcecao_quandoS3Falha() {
        S3Exception s3Ex = (S3Exception) S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("S3 error").build())
                .build();
        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(s3Ex);

        assertThatThrownBy(() ->
                s3StorageAdapter.downloadVideoAsStream("my-bucket", "user/video.mp4"))
                .isInstanceOf(VideoProcessingException.class);
    }

    // --- uploadFile ---

    @Test
    void uploadFile_deveRetornarKey_quandoSucesso(@TempDir Path dir) throws IOException {
        Path zipFile = dir.resolve("frames.zip");
        Files.write(zipFile, "zip content".getBytes());

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String result = s3StorageAdapter.uploadFile("my-bucket", "user/frames.zip", zipFile);

        assertThat(result).isEqualTo("user/frames.zip");
    }

    @Test
    void uploadFile_deveLancarExcecao_quandoS3Falha(@TempDir Path dir) throws IOException {
        Path zipFile = dir.resolve("frames.zip");
        Files.write(zipFile, "zip content".getBytes());

        S3Exception s3Ex = (S3Exception) S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("S3 error").build())
                .build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(s3Ex);

        assertThatThrownBy(() ->
                s3StorageAdapter.uploadFile("my-bucket", "user/frames.zip", zipFile))
                .isInstanceOf(VideoProcessingException.class);
    }

    // --- uploadFileFromStream (small file - simple upload) ---

    @Test
    void uploadFileFromStream_deveUsarUploadSimples_quandoArquivoPequeno() {
        byte[] data = "small".getBytes();
        InputStream inputStream = new ByteArrayInputStream(data);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String result = s3StorageAdapter.uploadFileFromStream("my-bucket", "user/f.zip", inputStream, data.length);

        assertThat(result).isEqualTo("user/f.zip");
    }

    @Test
    void uploadFileFromStream_deveLancarExcecao_quandoArquivoPequenoEFalha() {
        byte[] data = "small".getBytes();
        InputStream inputStream = new ByteArrayInputStream(data);

        S3Exception s3Ex = (S3Exception) S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("S3 error").build())
                .build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(s3Ex);

        assertThatThrownBy(() ->
                s3StorageAdapter.uploadFileFromStream("my-bucket", "user/f.zip", inputStream, data.length))
                .isInstanceOf(VideoProcessingException.class);
    }

    // --- fileExists ---

    @Test
    void fileExists_deveRetornarTrue_quandoArquivoExiste() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        boolean result = s3StorageAdapter.fileExists("my-bucket", "user/file.zip");

        assertThat(result).isTrue();
    }

    @Test
    void fileExists_deveRetornarFalse_quandoArquivoNaoExiste() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        boolean result = s3StorageAdapter.fileExists("my-bucket", "user/file.zip");

        assertThat(result).isFalse();
    }

    @Test
    void fileExists_deveLancarExcecao_quandoS3Falha() {
        S3Exception s3Ex = (S3Exception) S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("S3 error").build())
                .build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Ex);

        assertThatThrownBy(() ->
                s3StorageAdapter.fileExists("my-bucket", "user/file.zip"))
                .isInstanceOf(VideoProcessingException.class);
    }

    // --- uploadFileFromStream (large file - multipart upload) ---

    @Test
    void uploadFileFromStream_deveUsarMultipartUpload_quandoArquivoGrande() {
        // 6MB > 5MB threshold for multipart
        byte[] data = new byte[6 * 1024 * 1024];
        InputStream inputStream = new ByteArrayInputStream(data);

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload-123").build());
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag("etag-1").build());
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().build());

        String result = s3StorageAdapter.uploadFileFromStream("my-bucket", "user/f.zip", inputStream, data.length);

        assertThat(result).isEqualTo("user/f.zip");
        verify(s3Client).createMultipartUpload(any(CreateMultipartUploadRequest.class));
        verify(s3Client).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    void uploadFileMultipart_deveLancarExcecao_eAbortarUpload_quandoS3FalhaEmUploadPart() {
        byte[] data = new byte[6 * 1024 * 1024];
        InputStream inputStream = new ByteArrayInputStream(data);

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload-123").build());
        S3Exception s3Ex = (S3Exception) S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("S3 error").build())
                .build();
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class))).thenThrow(s3Ex);

        assertThatThrownBy(() ->
                s3StorageAdapter.uploadFileFromStream("my-bucket", "user/f.zip", inputStream, data.length))
                .isInstanceOf(VideoProcessingException.class);

        verify(s3Client).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }

    @Test
    void uploadFileMultipart_deveLancarExcecao_eAbortarUpload_quandoS3FalhaEmCreate() {
        byte[] data = new byte[6 * 1024 * 1024];
        InputStream inputStream = new ByteArrayInputStream(data);

        S3Exception s3Ex = (S3Exception) S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("S3 create error").build())
                .build();
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class))).thenThrow(s3Ex);

        assertThatThrownBy(() ->
                s3StorageAdapter.uploadFileFromStream("my-bucket", "user/f.zip", inputStream, data.length))
                .isInstanceOf(VideoProcessingException.class);
    }

    @Test
    void uploadFileMultipart_deveChamarDiretamente_quandoUsadoExplicitamente() {
        byte[] data = new byte[6 * 1024 * 1024];
        InputStream inputStream = new ByteArrayInputStream(data);

        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload-456").build());
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag("etag-1").build());
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().build());

        String result = s3StorageAdapter.uploadFileMultipart("my-bucket", "user/f.zip", inputStream, data.length);

        assertThat(result).isEqualTo("user/f.zip");
    }

    // --- getObjectMetadata ---

    @Test
    void getObjectMetadata_deveRetornarMetadados_quandoSucesso() {
        java.util.Map<String, String> metadata = java.util.Map.of("key1", "value1");
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().metadata(metadata).build());

        java.util.Map<String, String> result = s3StorageAdapter.getObjectMetadata("my-bucket", "user/file.zip");

        assertThat(result).containsEntry("key1", "value1");
    }

    @Test
    void getObjectMetadata_deveLancarExcecao_quandoS3Falha() {
        S3Exception s3Ex = (S3Exception) S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorMessage("S3 error").build())
                .build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(s3Ex);

        assertThatThrownBy(() ->
                s3StorageAdapter.getObjectMetadata("my-bucket", "user/file.zip"))
                .isInstanceOf(VideoProcessingException.class);
    }

    // --- downloadVideo: IOException path ---

    @Test
    void downloadVideo_deveLancarExcecao_quandoIOException() throws IOException {
        Path destPath = tempDir.resolve("subdir").resolve("video.mp4");

        // Create an InputStream that throws IOException on read
        InputStream ioFailStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk full");
            }
        };

        ResponseInputStream<GetObjectResponse> responseStream =
                new ResponseInputStream<>(GetObjectResponse.builder().build(), ioFailStream);
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        assertThatThrownBy(() ->
                s3StorageAdapter.downloadVideo("my-bucket", "user/video.mp4", destPath))
                .isInstanceOf(VideoProcessingException.class);
    }
}
