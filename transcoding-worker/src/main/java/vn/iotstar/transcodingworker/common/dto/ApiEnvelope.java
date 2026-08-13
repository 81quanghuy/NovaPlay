package vn.iotstar.transcodingworker.common.dto;

/** Khớp shape JSON của media-service's {@code GenericResponse} — chỉ phần worker cần đọc lại. */
public record ApiEnvelope<T>(Boolean success, String message, T result, int statusCode) {
}
