package vn.iotstar.emailservice.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import static vn.iotstar.authservice.util.MessageProperties.*;

@Data
public class EmailDTO {

    private String accountId;
    @NotBlank(message = PASSWORD_NOT_BLANK)
    @Size(min = 6, max = 100, message = PASSWORD_SIZE)
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = PASSWORD_COMPLEXITY
    )
    private String password;

    @Email(message = EMAIL_INVALID)
    @NotBlank(message = EMAIL_NOT_BLANK)
    @Size(max = 50, message = EMAIL_SIZE)
    private String email;

    private String ipAddress;
}
