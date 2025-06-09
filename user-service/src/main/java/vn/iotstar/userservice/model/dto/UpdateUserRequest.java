package vn.iotstar.userservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import static vn.iotstar.userservice.util.MessageProperties.USERNAME_NOT_BLANK;
import static vn.iotstar.userservice.util.MessageProperties.USERNAME_SIZE;

@Data
public class UpdateUserRequest {
    @NotBlank(message = USERNAME_NOT_BLANK)
    @Size(min = 3, max = 50, message = USERNAME_SIZE)
    private String userId;

    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$", message = USERNAME_SIZE)
    private String username;

    // file photo
    private String avatar;
    private String address;
    private String dayOfBirth;
    private String gender;


}
