package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import vn.iotstar.userservice.validation.BCP47Locale;

/**
 * Danh tính người dùng được lấy từ header {@code X-User-Email} do gateway inject,
 * nên request body không nhận email — đổi email phải đi qua auth-service.
 */
@Schema(name = "UpdateUserProfileRequest",
        description = "Yêu cầu cập nhật hồ sơ người dùng (domain profile, không chứa credential).")
public record UpdateUserProfileRequest(

        @Schema(description = "Username hiển thị/đăng nhập (tuỳ chính sách unique).",
                example = "huynguyen",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 100)
        String preferredUsername,

        @Schema(description = "Tên hiển thị trên UI.",
                example = "Huy Nguyễn",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 150)
        String displayName,

        @Schema(description = "Họ và tên (alias cho displayName)",
                example = "Huy Nguyễn",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 150)
        String fullName,

        @Schema(description = "Số điện thoại",
                example = "0987654321",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 20)
        String phoneNumber,

        @Schema(description = "Tiểu sử cá nhân",
                example = "Đam mê điện ảnh...",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 500)
        String bio,

        @Schema(description = "URL ảnh đại diện (CDN/media-service).",
                example = "https://cdn.novaplay.com/avatars/u-123.png",
                format = "uri",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 512)
        String avatarUrl,

        @Schema(description = "Ngôn ngữ/địa phương theo IETF BCP47.",
                example = "vi-VN",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 16) @BCP47Locale
        String locale,

        @Schema(description = "Đồng ý nhận marketing (email/SMS/push).",
                example = "true",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean marketingOptIn
) {}
