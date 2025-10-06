package vn.iotstar.utils.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "Request object for sending emails",
        title = "Email Request",
        requiredMode = Schema.RequiredMode.REQUIRED)
public record EmailRequest(
        @Schema(
                description = "Recipient email address",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "huy@gmail.com")
        String recipientEmail,

        @Schema(
                description = "recaptcha token for verification",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "03AGdBq24Z...")
        String captchaResponse,

        @Schema(
                description = "Client IP address",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "fcd3:4eac:5b6f:7a8b:9c0d:e1f2:3a4b:5c6d")
        String clientIp
) {
}
