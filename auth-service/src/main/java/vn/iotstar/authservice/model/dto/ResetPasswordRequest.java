package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(
        description = "Request to reset a user's password. This request does not require any fields as it is used to initiate the password reset process.")
public record ResetPasswordRequest(
        @Schema(
                description = "Email address of the user requesting the password reset. This is used to send the reset link.",
                example = "huy@gmail.com")
                @Email(message = "Email should be valid")
        String email,
        @Schema(
                description = "new password to set for the user. This is required to complete the password reset process.",
                example = "newPassword123")
                @NotBlank(message = "New password must not be blank")
        String newPassword,

        @Schema(
                description = "Confirmation of the new password. This should match the new password to confirm the reset.",
                example = "newPassword123"
        )
                @NotBlank(message = "Confirm new password must not be blank")
        String confirmNewPassword
) {
}
