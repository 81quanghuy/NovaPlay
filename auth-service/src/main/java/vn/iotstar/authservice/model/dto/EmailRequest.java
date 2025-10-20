package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import vn.iotstar.authservice.util.MessageProperties;

@Schema(
        description ="Request object for sending an email.",
        title = "EmailRequest",
        requiredMode = Schema.RequiredMode.REQUIRED)
public record EmailRequest(
        @Schema(
                description = "Recipient email address",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "ngoquanghuy0510@gmail.com")
        @NotBlank(message = MessageProperties.EMAIL_NOT_BLANK)
        @Email(message = MessageProperties.EMAIL_INVALID_FORMAT)
        String email,

        @Schema(description = "Ngôn ngữ của người dùng", example = "en")
        String locale
) {
}
