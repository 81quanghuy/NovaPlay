package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(
        description = "Request to reset a user's password. This request does not require any fields as it is used to initiate the password reset process.")
public record ResetPasswordRequest(
        @Schema(
                description = "Email address of the user requesting the password reset.",
                example = "huy@gmail.com")
                @Email(message = "Email should be valid")
        String email,

        @Schema(
                description = "OTP sent to the user's email for verification.",
                example = "123456")
                @NotBlank(message = "OTP must not be blank")
        String otp,

        @Schema(
                description = "New password to set for the user.",
                example = "newPassword123")
                @NotBlank(message = "New password must not be blank")
        String newPassword,

        @Schema(
                description = "Confirmation of the new password. Must match newPassword.",
                example = "newPassword123")
                @NotBlank(message = "Confirm new password must not be blank")
        String confirmNewPassword
) {
}
