package com.fiap.videoprocessor.infrastructure.adapter.rest;

import com.fiap.videoprocessor.domain.exception.VideoProcessingException;
import com.fiap.videoprocessor.domain.model.ProcessingResult;
import com.fiap.videoprocessor.domain.ports.input.ProcessVideoUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Controlador REST para processamento de vídeos
 */
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
@Slf4j
public class VideoProcessingController {
    
    private final ProcessVideoUseCase processVideoUseCase;
    
    @PostMapping("/process")
    public ResponseEntity<ProcessingResponse> processVideo(
            @Valid @RequestBody ProcessVideoRequest request) {
        
        log.info("Recebida requisição de processamento: {}", request);
        
        try {
            // Se outputBucket não for especificado, usar o mesmo bucket do vídeo
            String outputBucket = request.outputBucket() != null && !request.outputBucket().isBlank() 
                    ? request.outputBucket() 
                    : request.inputBucket();
            
            ProcessingResult result = processVideoUseCase.process(
                    request.videoId(),
                    request.s3Key(),
                    request.inputBucket(),
                    outputBucket
            );
            
            ProcessingResponse response = ProcessingResponse.fromDomain(result);
            return ResponseEntity.ok(response);
            
        } catch (VideoProcessingException e) {
            log.error("Erro ao processar vídeo", e);
            
            ProcessingResponse errorResponse = new ProcessingResponse(
                    request.videoId(),
                    false,
                    "Erro ao processar vídeo: " + e.getMessage(),
                    0,
                    null,
                    0
            );
            
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "Video Processing Worker"));
    }
    
    /**
     * Request DTO
     */
    public record ProcessVideoRequest(
            @NotBlank(message = "videoId é obrigatório")
            String videoId,
            
            @NotBlank(message = "s3Key é obrigatório")
            String s3Key,
            
            @NotBlank(message = "inputBucket é obrigatório")
            String inputBucket,
            
            String outputBucket  // Opcional - se não informado, usa o mesmo bucket do vídeo
    ) {}
    
    /**
     * Response DTO
     */
    public record ProcessingResponse(
            String videoId,
            boolean success,
            String message,
            int frameCount,
            String zipS3Key,
            long processingTimeMs
    ) {
        public static ProcessingResponse fromDomain(ProcessingResult result) {
            return new ProcessingResponse(
                    result.getVideoId(),
                    result.isSuccess(),
                    result.getMessage(),
                    result.getFrameCount(),
                    result.getZipS3Key(),
                    result.getProcessingTimeMs()
            );
        }
    }
    
    /**
     * Health check response
     */
    public record HealthResponse(
            String status,
            String service
    ) {}
}
