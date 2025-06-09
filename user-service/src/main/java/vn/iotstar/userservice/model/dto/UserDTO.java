package vn.iotstar.userservice.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import static vn.iotstar.userservice.util.MessageProperties.*;

@Data
public class UserDTO {

    @Email(message = EMAIL_INVALID)
    @NotBlank(message = EMAIL_NOT_BLANK)
    @Size(max = 50, message = EMAIL_SIZE)
    private String email;

    private String accountId;

}
