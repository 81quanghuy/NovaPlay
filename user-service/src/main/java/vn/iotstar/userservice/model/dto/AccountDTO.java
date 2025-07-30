package vn.iotstar.userservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import static vn.iotstar.userservice.util.MessageProperties.*;


@Schema(
        description = "Data Transfer Object for user account information",
        title = "AccountDTO"
)
@Data
public class AccountDTO {

    @Schema(
            description = "Unique identifier for the account",
            example = "1234567890",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String accountId;

    @Schema(
            description = "Password for the account",
            example = "StrongPassword123",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = PASSWORD_NOT_BLANK)
    @Size(min = 6, max = 100, message = PASSWORD_SIZE)
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = PASSWORD_COMPLEXITY
    )
    private String password;


    @Schema(
            description = "Email address associated with the account",
            example = "ngoquanghuy0310@gmail.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @Email(message = EMAIL_INVALID)
    @NotBlank(message = EMAIL_NOT_BLANK)
    @Size(max = 50, message = EMAIL_SIZE)
    private String email;

    @Schema(
            description = "IP address of the user",
            example = "128.196.1.1",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    private String ipAddress;
}
