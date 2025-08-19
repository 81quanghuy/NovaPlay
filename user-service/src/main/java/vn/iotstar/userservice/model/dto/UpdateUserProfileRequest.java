package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.iotstar.userservice.validation.BCP47Locale;

@Schema(name = "UpdateUserProfileRequest",
        description = "Yêu cầu cập nhật hồ sơ người dùng (domain profile, không chứa credential).")
public record UpdateUserProfileRequest(

        @Schema(description = "Email của người dùng (case-insensitive).",
                example = "user@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Email @Size(max = 255)
        String email,

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

        @Schema(description = "Tên gói cước snapshot để hiển thị (nguồn chân lý ở billing).",
                example = "Premium",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 50)
        String plan,

        @Schema(description = "Đồng ý nhận marketing (email/SMS/push).",
                example = "true",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean marketingOptIn
) {}
