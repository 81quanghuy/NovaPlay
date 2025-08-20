package vn.iotstar.authservice.mapper;

import vn.iotstar.authservice.model.dto.UserCreationRequest;
import vn.iotstar.authservice.model.dto.UserResponse;
import vn.iotstar.authservice.model.entity.User;
import java.util.stream.Collectors;

public class UserMapper {

    /**
     * Converts a User entity to a UserResponse DTO.
     * @param user the User entity to convert
     * @return the converted UserResponse DTO, or null if user is null
     */
    public static UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getIsActive(),
                user.getIsEmailVerified(),
                user.getLastLoginAt(),
                user.getRoles().stream()
                        .map(RoleMapper::toRoleDTO)
                        .collect(Collectors.toSet())
        );
    }

    /**
     * Converts a UserCreationRequest to a User entity.
     * @param request the UserCreationRequest to convert
     * @return the converted User entity, or null if request is null
     */
    public static User toUser(UserCreationRequest request) {
        if (request == null) {
            return null;
        }
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(request.password());
        user.setIsActive(true);
        user.setIsEmailVerified(false);
        return user;
    }
}