package vn.iotstar.userservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;

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
}
