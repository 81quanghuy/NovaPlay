package vn.iotstar.transcodingworker.common.dto;

/** Chỉ mang field worker thực sự cần đọc: kiểm tra idempotency trước khi xử lý. */
public record VideoManifestDto(String id, String sourceMediaId, String status) {
}
