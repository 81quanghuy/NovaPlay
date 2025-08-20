package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
        description = "Request to change user password",
        title = "Change Password Request")
public record ChangePasswordRequest (
        @Schema(description = "Current password of the user", example = "currentPassword123")
                @NotBlank(message = "Current password must not be blank")
        String currentPassword,

        @Schema(description = "New password for the user", example = "newPassword456")
                @NotBlank(message = "New password must not be blank")
        String newPassword,
        @Schema(description = "Confirmation of the new password", example = "newPassword456")
                @NotBlank(message = "Confirm new password must not be blank")
        String confirmNewPassword
) {
}
