package vn.iotstar.userservice.controller;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.userservice.model.dto.UpdateUserRequest;
import vn.iotstar.userservice.model.dto.UserDTO;
import vn.iotstar.userservice.service.impl.UserService;

import static vn.iotstar.userservice.util.Constants.PARAM_USER_ID;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final Environment environment;

    /**
     * Register a new user
     *
     * @param pUserDTO User data transfer object containing user details
     * @return ResponseEntity with GenericResponse indicating success or failure
     */
    @PostMapping("/create")
    public ResponseEntity<GenericResponse> register(@Valid @RequestBody UserDTO pUserDTO) {
        log.info("Registering user: {}", pUserDTO.getEmail());
        return userService.createUser(pUserDTO);
    }

    /**
     * Update user information
     *
     * @param pUserDTO User data transfer object containing updated user details
     * @return ResponseEntity with GenericResponse indicating success or failure
     */
    @PutMapping("/update")
    public ResponseEntity<GenericResponse> updateUser(@Valid @RequestBody UpdateUserRequest pUserDTO) {
        log.info("Updating user: {}", pUserDTO.getUsername());
        return userService.updateUser(pUserDTO);
    }

    /**
     * Get user by ID
     *
     * @param userId ID of the user to be fetched
     * @return ResponseEntity with GenericResponse containing user details
     */
    @GetMapping("/{userId}")
    public ResponseEntity<GenericResponse> getUserById(@PathVariable(PARAM_USER_ID) String userId) {
        log.info("Fetching user by ID: {}", userId);
        return userService.getUserById(userId);
    }

    /**
     * Delete user by ID
     *
     * @param userId ID of the user to be deleted
     * @return ResponseEntity with GenericResponse indicating success or failure
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<GenericResponse> deleteUserById(@PathVariable(PARAM_USER_ID) String userId) {
        log.info("Deleting user by ID: {}", userId);
        return userService.deleteUserById(userId);
    }

    @Retry(name = "getContactInfo",fallbackMethod = "getContactInfoFallback")
    @GetMapping("/contact-info")
    public ResponseEntity<GenericResponse> getContactInfo() {

        log.info("Fetching contact information");
        GenericResponse response = new GenericResponse();
        response.setSuccess(true);
        response.setMessage("Contact information retrieved successfully.");
        response.setResult("For support, please contact us at ");
        response.setStatusCode(HttpStatus.OK.value());
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<GenericResponse> getContactInfoFallback(Exception e) {
        log.error("Error fetching contact information: {}", e.getMessage());
        GenericResponse response = new GenericResponse();
        response.setSuccess(false);
        response.setMessage("Failed to fetch contact information. Please try again later.");
        response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @RateLimiter(name= "getJavaVersion", fallbackMethod = "getJavaVersionFallback")
    @GetMapping("/java-version")
    public ResponseEntity<String> getJavaVersion() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(environment.getProperty("JAVA_HOME"));
    }

    public ResponseEntity<String> getJavaVersionFallback(Exception e) {
        log.error("Error fetching Java version: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to fetch Java version. Please try again later.");
    }
}
