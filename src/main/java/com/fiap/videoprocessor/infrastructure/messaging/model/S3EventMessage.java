package com.fiap.videoprocessor.infrastructure.messaging.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Modelo para evento S3 recebido via SQS
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3EventMessage {
    
    @JsonProperty("Records")
    private List<S3EventRecord> records;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class S3EventRecord {
        private String eventVersion;
        private String eventSource;
        private String awsRegion;
        private String eventTime;
        private String eventName;
        private S3Data s3;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class S3Data {
        private String s3SchemaVersion;
        private String configurationId;
        private S3Bucket bucket;
        private S3Object object;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class S3Bucket {
        private String name;
        private String arn;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class S3Object {
        private String key;
        private Long size;
        private String eTag;
        private String sequencer;
    }
}
