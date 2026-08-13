package vn.iotstar.transcodingworker.common.dto;

/** Bản sao độc lập của media-service's event DTO — wire contract là JSON, không phải class Java. */
public record VideoSourceReadyEvent(
        String manifestId,
        String sourceMediaId,
        String ownerId,
        String ownerEmail,
        String s3Key,
        String storageProvider,
        String contentType,
        Long sizeBytes
) {
}
