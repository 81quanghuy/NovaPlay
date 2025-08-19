package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

@Schema(name = "UserProfileDTO",
        description = "Data Transfer Object for User Profile, used in API responses.")
public record UserProfileDTO(
        @Schema(description = "ID của người dùng, định dạng UUID.",
                example = "123e4567-e89b-12d3-a456-426614174000")
        UUID userId,

        @Schema(description = "Tên hiển thị của người dùng.",
                example = "john_doe")
        String displayName,

        @Schema(description = "Email của người dùng.",
                example = "quanghuy@gmail.com")
        @Email
        @NotBlank
        String email

){}
