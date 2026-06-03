package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.iotstar.userservice.mapper.UserProfileMapper;
import vn.iotstar.userservice.model.dto.UpdateUserProfileRequest;
import vn.iotstar.userservice.model.dto.UserProfileDTO;
import vn.iotstar.userservice.model.entity.UserProfile;
import vn.iotstar.userservice.repository.IUserProfileRepository;
import vn.iotstar.userservice.service.UserProfileService;
import vn.iotstar.userservice.service.client.MediaServiceClient;
import vn.iotstar.userservice.util.Constants;
import vn.iotstar.userservice.util.Plan;
import vn.iotstar.utils.dto.UploadRequestDto;
import vn.iotstar.utils.dto.UploadResponseDto;
import vn.iotstar.utils.dto.UserRegister;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final MediaServiceClient mediaServiceClient;
    private final IUserProfileRepository userProfileRepository;

    @Override
    @Transactional
    public void registerNewUser(UserRegister evt) {
        log.info("Registering new user profile for user: {}, email: {}", evt.username(), evt.email());
        UserProfile userProfile = UserProfile.builder()
                .displayName(evt.username())
                .email(evt.email())
                .avatarUrl(Constants.DEFAULT_AVATAR_URL)
                .plan(Plan.MEMBER)
                .build();
        userProfile.normalize();
        userProfileRepository.save(userProfile);
    }

    @Override
    public UserProfileDTO getProfile(String email) {
        UserProfile profile = findByEmailOrThrow(email);
        return UserProfileMapper.toUserProfileDTO(profile);
    }

    @Override
    @Transactional
    public UserProfileDTO updateProfile(String email, UpdateUserProfileRequest request) {
        UserProfile profile = findByEmailOrThrow(email);

        if (request.preferredUsername() != null) {
            profile.setPreferredUsername(request.preferredUsername());
        }
        if (request.displayName() != null) {
            profile.setDisplayName(request.displayName());
        }
        if (request.locale() != null) {
            profile.setLocale(request.locale());
        }
        if (request.marketingOptIn() != null) {
            boolean current = profile.isMarketingOptIn();
            profile.setMarketingOptIn(request.marketingOptIn());
            if (!current && request.marketingOptIn()) {
                profile.setMarketingOptInAt(Instant.now());
            }
        }

        profile.normalize();
        userProfileRepository.save(profile);
        return UserProfileMapper.toUserProfileDTO(profile);
    }

    @Override
    public UploadResponseDto changeAvatar(UploadRequestDto uploadRequestDto, String email, String traceId) {
        log.info("Changing avatar for email: {} | traceId: {}", email, traceId);
        UserProfile userProfile = findByEmailOrThrow(email);
        UploadRequestDto request = new UploadRequestDto(userProfile.getId(), uploadRequestDto.fileName(),
                uploadRequestDto.contentType(), uploadRequestDto.size());
        return mediaServiceClient.requestUploadUrl(request);
    }

    @Override
    @Transactional
    public void changeUrlAvatar(String url, String userId) {
        log.info("Changing avatar URL to {} for userId: {}", url, userId);
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));
        userProfile.setAvatarUrl(url);
        userProfileRepository.save(userProfile);
    }

    private UserProfile findByEmailOrThrow(String email) {
        return userProfileRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));
    }
}
