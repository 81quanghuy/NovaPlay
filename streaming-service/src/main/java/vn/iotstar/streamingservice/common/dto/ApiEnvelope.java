package vn.iotstar.streamingservice.common.dto;

/** Khớp shape JSON của {@code GenericResponse} bên movie-service/media-service. */
public record ApiEnvelope<T>(Boolean success, String message, T result, int statusCode) {
}
