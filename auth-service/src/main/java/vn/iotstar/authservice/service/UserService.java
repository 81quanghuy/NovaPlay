package vn.iotstar.authservice.service;

import vn.iotstar.authservice.model.dto.UserCreationRequest;
import vn.iotstar.authservice.model.dto.UserResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserService {

    /**
     * Creates a new user from registration information.
     * @param request A DTO containing the user creation details.
     * @return A DTO containing the newly created user's information.
     */
    UserResponse registerNewUser(UserCreationRequest request);

    /**
     * Finds a user by their ID.
     * @param id The ID of the user.
     * @return an Optional containing the UserResponse if found.
     */
    Optional<UserResponse> findUserById(UUID id);

    /**
     * Finds a user by their email.
     * @param email The user's email.
     * @return an Optional containing the UserResponse if found.
     */
    Optional<UserResponse> findUserByEmail(String email);

    /**
     * Retrieves a list of all users.
     * @return A list of UserResponse DTOs.
     */
    List<UserResponse> getAllUsers();
}