package vn.iotstar.streamingservice.service.client.dto;

/** Chỉ mang field streaming-service thực sự cần từ user-service's {@code UserProfileDTO}. */
public record UserProfileResponse(String email, String plan) {
}
