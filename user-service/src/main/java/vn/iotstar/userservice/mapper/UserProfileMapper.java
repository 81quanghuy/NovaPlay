package vn.iotstar.userservice.mapper;

import vn.iotstar.userservice.model.dto.UserProfileDTO;
import vn.iotstar.userservice.model.entity.UserProfile;

/**
 * Mapper class for converting between UserProfile entity and UserProfileDTO.
 * This class provides static methods to map between the two types.
 * @author Quanghuy81
 * @version 1.0
 */
public class UserProfileMapper {

    /**
     * Maps a UserProfile entity to a UserProfileDTO.
     *
     * @param userProfile the UserProfile entity to map
     * @return the mapped UserProfileDTO
     */
    public static UserProfileDTO toUserProfileDTO(UserProfile userProfile) {
        return new UserProfileDTO(
                userProfile.getId(),
                userProfile.getPreferredUsername(),
                userProfile.getEmail()
        );
    }

    /**
     * Maps a UserProfileDTO to a UserProfile entity.
     *
     * @param userProfileDTO the UserProfileDTO to map
     * @return the mapped UserProfile entity
     */
    public static UserProfile toUserProfile(UserProfileDTO userProfileDTO) {
        UserProfile userProfile = new UserProfile();
        userProfile.setId(userProfileDTO.userId());
        userProfile.setDisplayName(userProfileDTO.displayName());
        userProfile.setEmail(userProfileDTO.email());
        return userProfile;
    }
}
