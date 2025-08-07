package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.userservice.model.dto.UpdateUserRequest;
import vn.iotstar.userservice.model.dto.UserDTO;
import vn.iotstar.userservice.model.entity.User;
import vn.iotstar.userservice.repository.IUserRepository;
import vn.iotstar.userservice.service.IUserSerivce;
import vn.iotstar.userservice.util.Constants;
import vn.iotstar.userservice.util.Gender;

import java.time.LocalDate;
import java.util.Date;

import static vn.iotstar.userservice.util.MessageProperties.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService implements IUserSerivce {

    private final IUserRepository userRepository;
    /**
     * This method is used to create a new user.
     *
     * @param UserDto the user data transfer object containing user information
     * @return ResponseEntity<GenericResponse> response entity containing the result of the operation
     */
    @Override
    public ResponseEntity<GenericResponse> createUser(UserDTO UserDto) {
        log.info("Creating user with email: {}", UserDto.getEmail());
        // Check if user already exists
        if (userRepository.existsByAccountId(UserDto.getAccountId())) {
            log.error("User with account ID {} already exists", UserDto.getAccountId());
            return ResponseEntity.badRequest().body(GenericResponse.builder()
                    .success(false)
                    .message(USER_ALREADY_EXISTS)
                    .statusCode(400)
                    .build());
        }
        // edit username for email : cut @domain.com
        String username = UserDto.getEmail().substring(0, UserDto.getEmail().indexOf("@"));
        User user = User.builder()
                .username(username)
                .accountId(UserDto.getAccountId())
                .avatar(Constants.DEFAULT_AVATAR_URL)
                .build();
        userRepository.save(user);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(USER_CREATE_SUCCESS)
                .result(user)
                .statusCode(200)
                .build());
    }

    @Override
    public ResponseEntity<GenericResponse> updateUser(UpdateUserRequest pUserDTO) {
        log.info("Updating user with username: {}", pUserDTO.getUsername());
        User user = userRepository.findById(pUserDTO.getUserId())
                .orElseThrow(() -> new RuntimeException(USER_NOT_FOUND + pUserDTO.getUserId()));
        // Update user details : check if the fields are not null before updating
        if (pUserDTO.getUsername() != null) {
            user.setUsername(pUserDTO.getUsername());
        }
        if (pUserDTO.getAvatar() != null) {
            user.setAvatar(pUserDTO.getAvatar());
        }
        if( pUserDTO.getAddress() != null) {
            user.setAddress(pUserDTO.getAddress());
        }
        if( pUserDTO.getDayOfBirth() != null) {
            Date dayOfBirth = Date.from(LocalDate.parse(pUserDTO.getDayOfBirth()).atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
            user.setDayOfBirth(dayOfBirth);
        }
        if( pUserDTO.getGender() != null) {
            user.setGender(Gender.valueOf(pUserDTO.getGender()));
        }

        userRepository.save(user);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(USER_UPDATE_SUCCESS)
                .result(user)
                .statusCode(200)
                .build());
    }

    /**
     * This method is used to get a user by their ID.
     *
     * @param userId the ID of the user to retrieve
     * @return ResponseEntity<GenericResponse> response entity containing the user information
     */
    @Override
    public ResponseEntity<GenericResponse> getUserById(String userId) {
        log.info("Fetching user by ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(USER_NOT_FOUND + userId));
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(USER_GET_SUCCESS)
                .result(user)
                .statusCode(200)
                .build());
    }

    /**
     * This method is used to delete a user by their ID.
     *
     * @param userId the ID of the user to delete
     * @return ResponseEntity<GenericResponse> response entity indicating the result of the deletion
     */
    @Override
    public ResponseEntity<GenericResponse> deleteUserById(String userId) {
        log.info("Deleting user by ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException(USER_NOT_FOUND + userId));
        userRepository.delete(user);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(USER_DELETE_SUCCESS)
                .result(null)
                .statusCode(200)
                .build());
    }

}
