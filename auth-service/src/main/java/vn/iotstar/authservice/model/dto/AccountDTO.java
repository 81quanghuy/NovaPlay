package vn.iotstar.authservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import static vn.iotstar.authservice.util.MessageProperties.*;

@Data
public class AccountDTO {

    @NotBlank(message = USERNAME_NOT_BLANK)
    @Size(min = 4, max = 20, message = USERNAME_SIZE)
    private String username;

    @NotBlank(message = PASSWORD_NOT_BLANK)
    @Size(min = 6, max = 100, message = PASSWORD_SIZE)
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
            message = PASSWORD_COMPLEXITY
    )
    private String password;

    private String ipAddress;
}
