package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

@Schema(description = "Request object for email operations")
public record EmailRequest (
        @Schema(description = "The email address to be used for operations such as password reset or verification",
                example = "huffy@.gmail.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @Email(message = "Invalid email format")
        String email
) {
}
