package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserProfileDTO", description = "Thông tin hồ sơ người dùng trả về trong API responses.")
public record UserProfileDTO(
        @Schema(description = "ID của người dùng.", example = "a1b2c3d4-...")
        String userId,

        @Schema(description = "Email của người dùng.", example = "user@example.com")
        String email,

        @Schema(description = "Username hiển thị/đăng nhập.", example = "huynguyen")
        String preferredUsername,

        @Schema(description = "Tên hiển thị trên UI.", example = "Huy Nguyễn")
        String displayName,

        @Schema(description = "URL ảnh đại diện.", example = "https://cdn.novaplay.com/avatars/u-123.png")
        String avatarUrl,

        @Schema(description = "Ngôn ngữ theo BCP47.", example = "vi-VN")
        String locale,

        @Schema(description = "Gói cước.", example = "MEMBER")
        String plan,

        @Schema(description = "Trạng thái tài khoản.")
        boolean active,

        @Schema(description = "Đồng ý nhận marketing.")
        boolean marketingOptIn
) {}
