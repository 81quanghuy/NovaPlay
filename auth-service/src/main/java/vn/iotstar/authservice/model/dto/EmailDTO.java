package vn.iotstar.authservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import vn.iotstar.authservice.util.MessageProperties;


@Schema(
        description = "Data Transfer Object for Email operations",
        title = "Email DTO",
        requiredMode = Schema.RequiredMode.REQUIRED)
public record EmailDTO(

        @Schema(
                description = "Recipient email address",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "ngoquanghuy0510@gmail.com")
        @NotBlank(message = MessageProperties.EMAIL_NOT_BLANK)
        @Email(message = MessageProperties.EMAIL_INVALID_FORMAT)
        String email,

        @Schema(
                description = "One-Time Password (OTP) for email verfication",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "123456")
        @NotBlank(message = MessageProperties.OTP_NOT_BLANK)
        String otp,

        @Schema(
                description = "Expiration time for the OTP",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "2023-10-01T12:00:00Z"
        )
        String expirationTime
){}
