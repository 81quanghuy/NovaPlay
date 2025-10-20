package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        title = "AvatarRequest",
        description = "Request object for updating user avatar.",
        requiredMode = Schema.RequiredMode.REQUIRED)
public record AvatarRequest (
        @Schema(description = "email for user",
                example = "m@gmail.com")
        String email,
        @Schema(description = "new avatar url",
                example = "https://example.com/avatar.jpg")
        String avatarUrl

){
}
