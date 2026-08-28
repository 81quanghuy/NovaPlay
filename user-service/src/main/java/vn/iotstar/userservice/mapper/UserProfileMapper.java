package vn.iotstar.userservice.mapper;

import vn.iotstar.userservice.model.dto.UserProfileDTO;
import vn.iotstar.userservice.model.entity.UserProfile;

public class UserProfileMapper {

    private UserProfileMapper() {}

    public static UserProfileDTO toUserProfileDTO(UserProfile p) {
        return new UserProfileDTO(
                p.getId(),
                p.getEmail(),
                p.getPreferredUsername(),
                p.getDisplayName(),
                p.getDisplayName(), // fullName
                p.getPhoneNumber(),
                p.getBio(),
                p.getAvatarUrl(),
                p.getLocale(),
                p.getPlan() != null ? p.getPlan().name() : null,
                p.isActive(),
                p.isMarketingOptIn()
        );
    }
}
