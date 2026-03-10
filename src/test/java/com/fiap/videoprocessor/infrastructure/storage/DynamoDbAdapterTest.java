package com.fiap.videoprocessor.infrastructure.storage;

import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.domain.model.VideoDynamoModel;
import com.fiap.videoprocessor.infrastructure.adapter.storage.DynamoDbAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoDbAdapterTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private DynamoDbAdapter dynamoDbAdapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dynamoDbAdapter, "tableName", "test-table");
    }

    // --- updateStatusToProcessing ---

    @Test
    void updateStatusToProcessing_deveAtualizar_comSucesso() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        dynamoDbAdapter.updateStatusToProcessing("user/video.mp4", "user1", "videoId1");

        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void updateStatusToProcessing_deveIgnorar_quandoRegistroNaoExiste() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("not found").build());

        // Should not throw
        dynamoDbAdapter.updateStatusToProcessing("user/video.mp4", "user1", "videoId1");

        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void updateStatusToProcessing_deveLancarExcecao_quandoDynamoFalha() {
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("connection error").build());

        assertThatThrownBy(() ->
                dynamoDbAdapter.updateStatusToProcessing("user/video.mp4", "user1", "videoId1"))
                .isInstanceOf(VideoProcessingException.class);
    }

    // --- updateStatusToSuccess ---

    @Test
    void updateStatusToSuccess_deveAtualizar_quandoVideoEncontrado() {
        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.builder().s("videoId1").build(),
                "userId", AttributeValue.builder().s("user1").build(),
                "videoKey", AttributeValue.builder().s("user1/videoId1.mp4").build(),
                "status", AttributeValue.builder().s("uploaded").build()
        );
        QueryResponse queryResponse = QueryResponse.builder().items(List.of(item)).build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        dynamoDbAdapter.updateStatusToSuccess("user1/videoId1.mp4", "user1/videoId1.zip");

        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void updateStatusToSuccess_deveIgnorar_quandoVideoNaoEncontrado() {
        QueryResponse queryResponse = QueryResponse.builder().items(List.of()).build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Should not throw
        dynamoDbAdapter.updateStatusToSuccess("user1/videoId1.mp4", "user1/videoId1.zip");

        verify(dynamoDbClient, never()).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void updateStatusToSuccess_deveIgnorar_quandoCondicaoDeFalhaNaVerificacao() {
        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.builder().s("videoId1").build(),
                "userId", AttributeValue.builder().s("user1").build()
        );
        QueryResponse queryResponse = QueryResponse.builder().items(List.of(item)).build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("cond failed").build());

        // Should not throw
        dynamoDbAdapter.updateStatusToSuccess("user1/videoId1.mp4", "user1/videoId1.zip");
    }

    @Test
    void updateStatusToSuccess_deveLancarExcecao_quandoDynamoFalha() {
        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.builder().s("videoId1").build(),
                "userId", AttributeValue.builder().s("user1").build()
        );
        QueryResponse queryResponse = QueryResponse.builder().items(List.of(item)).build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("error").build());

        assertThatThrownBy(() ->
                dynamoDbAdapter.updateStatusToSuccess("user1/videoId1.mp4", "user1/videoId1.zip"))
                .isInstanceOf(VideoProcessingException.class);
    }

    // --- updateStatusToError ---

    @Test
    void updateStatusToError_deveAtualizar_quandoVideoEncontrado() {
        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.builder().s("videoId1").build(),
                "userId", AttributeValue.builder().s("user1").build()
        );
        QueryResponse queryResponse = QueryResponse.builder().items(List.of(item)).build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        dynamoDbAdapter.updateStatusToError("user1/videoId1.mp4", "error occurred");

        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void updateStatusToError_deveIgnorar_quandoVideoNaoEncontrado() {
        QueryResponse queryResponse = QueryResponse.builder().items(List.of()).build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        dynamoDbAdapter.updateStatusToError("user1/videoId1.mp4", "error occurred");

        verify(dynamoDbClient, never()).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void updateStatusToError_deveContinuar_quandoDynamoFalha() {
        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.builder().s("videoId1").build(),
                "userId", AttributeValue.builder().s("user1").build()
        );
        QueryResponse queryResponse = QueryResponse.builder().items(List.of(item)).build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("error").build());

        // Should not throw — error is logged and swallowed
        dynamoDbAdapter.updateStatusToError("user1/videoId1.mp4", "error occurred");
    }

    // --- findByVideoKey ---

    @Test
    void findByVideoKey_deveRetornarVideo_quandoEncontrado() {
        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.builder().s("videoId1").build(),
                "userId", AttributeValue.builder().s("user1").build(),
                "videoKey", AttributeValue.builder().s("user1/videoId1.mp4").build(),
                "name", AttributeValue.builder().s("meu-video").build(),
                "status", AttributeValue.builder().s("uploaded").build(),
                "createdAt", AttributeValue.builder().s("2024-01-01T00:00:00+00:00").build(),
                "updatedAt", AttributeValue.builder().s("2024-01-01T01:00:00+00:00").build()
        );
        QueryResponse queryResponse = QueryResponse.builder().items(List.of(item)).build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        Optional<VideoDynamoModel> result = dynamoDbAdapter.findByVideoKey("user1/videoId1.mp4");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("videoId1");
        assertThat(result.get().getUserId()).isEqualTo("user1");
        assertThat(result.get().getName()).isEqualTo("meu-video");
    }

    @Test
    void findByVideoKey_deveRetornarVazio_quandoNaoEncontrado() {
        QueryResponse queryResponse = QueryResponse.builder().items(List.of()).build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        Optional<VideoDynamoModel> result = dynamoDbAdapter.findByVideoKey("user1/videoId1.mp4");

        assertThat(result).isEmpty();
    }

    @Test
    void findByVideoKey_deveRetornarVazio_quandoKeyFormatoInvalido() {
        Optional<VideoDynamoModel> result = dynamoDbAdapter.findByVideoKey("invalid-key-no-slash");

        assertThat(result).isEmpty();
        verify(dynamoDbClient, never()).query(any(QueryRequest.class));
    }

    @Test
    void findByVideoKey_deveLancarExcecao_quandoDynamoFalha() {
        when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenThrow(DynamoDbException.builder().message("error").build());

        assertThatThrownBy(() -> dynamoDbAdapter.findByVideoKey("user1/videoId1.mp4"))
                .isInstanceOf(VideoProcessingException.class);
    }

    @Test
    void findByVideoKey_deveTolerateDataInvalida() {
        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.builder().s("videoId1").build(),
                "userId", AttributeValue.builder().s("user1").build(),
                "createdAt", AttributeValue.builder().s("data-invalida").build()
        );
        QueryResponse queryResponse = QueryResponse.builder().items(List.of(item)).build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        Optional<VideoDynamoModel> result = dynamoDbAdapter.findByVideoKey("user1/videoId1.mp4");

        assertThat(result).isPresent();
        assertThat(result.get().getCreatedAt()).isNull();
    }
}
