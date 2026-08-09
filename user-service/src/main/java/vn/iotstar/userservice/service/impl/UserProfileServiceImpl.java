package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import vn.iotstar.userservice.mapper.UserProfileMapper;
import vn.iotstar.userservice.model.dto.UpdateUserProfileRequest;
import vn.iotstar.userservice.model.dto.UserProfileDTO;
import vn.iotstar.userservice.model.entity.UserProfile;
import vn.iotstar.userservice.config.observability.UserMetrics;
import vn.iotstar.userservice.repository.IUserProfileRepository;
import vn.iotstar.userservice.service.AuditLogger;
import vn.iotstar.userservice.service.UserProfileLookup;
import vn.iotstar.userservice.service.UserProfileService;
import vn.iotstar.userservice.service.client.MediaServiceClient;
import vn.iotstar.userservice.util.Constants;
import vn.iotstar.userservice.util.Plan;
import vn.iotstar.userservice.common.dto.UploadRequestDto;
import vn.iotstar.userservice.common.dto.UploadResponseDto;
import vn.iotstar.userservice.common.dto.UserRegister;
import vn.iotstar.userservice.exception.ResourceNotFoundException;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final MediaServiceClient mediaServiceClient;
    private final IUserProfileRepository userProfileRepository;
    private final UserProfileLookup userProfileLookup;
    private final UserMetrics metrics;
    private final AuditLogger auditLogger;

    /**
     * Kafka giao message theo ngữ nghĩa at-least-once, nên method này phải idempotent:
     * một event bị giao lại không được tạo profile thứ hai.
     */
    @Override
    public void registerNewUser(UserRegister evt) {
        if (userProfileRepository.findByEmail(evt.email()).isPresent()) {
            log.info("Profile already exists for email {}, skipping duplicate event", evt.email());
            return;
        }

        log.info("Registering new user profile for user: {}, email: {}", evt.username(), evt.email());
        UserProfile userProfile = UserProfile.builder()
                .displayName(evt.username())
                .email(evt.email())
                .avatarUrl(Constants.DEFAULT_AVATAR_URL)
                .plan(Plan.MEMBER)
                .build();
        userProfile.normalize();

        try {
            userProfileRepository.save(userProfile);
            auditLogger.profileRegistered(evt.email());
        } catch (DuplicateKeyException e) {
            // Hai consumer cùng xử lý một event: unique index trên email đã chặn bản ghi thứ hai.
            log.info("Concurrent registration for email {} lost the race, profile already created", evt.email());
        }
    }

    @Override
    public UserProfileDTO getProfile(String email) {
        UserProfile profile = userProfileLookup.findByEmailOrThrow(email);
        return UserProfileMapper.toUserProfileDTO(profile);
    }

    @Override
    public UserProfileDTO updateProfile(String email, UpdateUserProfileRequest request) {
        UserProfile profile = userProfileLookup.findByEmailOrThrow(email);

        if (request.preferredUsername() != null) {
            profile.setPreferredUsername(request.preferredUsername());
        }
        if (request.displayName() != null) {
            profile.setDisplayName(request.displayName());
        }
        if (request.avatarUrl() != null) {
            profile.setAvatarUrl(request.avatarUrl());
        }
        if (request.locale() != null) {
            profile.setLocale(request.locale());
        }
        if (request.plan() != null) {
            profile.setPlan(request.plan());
        }
        if (request.marketingOptIn() != null) {
            boolean current = profile.isMarketingOptIn();
            profile.setMarketingOptIn(request.marketingOptIn());
            if (!current && request.marketingOptIn()) {
                profile.setMarketingOptInAt(Instant.now());
            }
        }

        profile.normalize();
        UserProfile saved = userProfileRepository.save(profile);
        metrics.getProfileUpdated().increment();
        auditLogger.profileUpdated(email);
        return UserProfileMapper.toUserProfileDTO(saved);
    }

    @Override
    public UploadResponseDto changeAvatar(UploadRequestDto uploadRequestDto, String email, String traceId) {
        log.info("Changing avatar for email: {} | traceId: {}", email, traceId);
        UserProfile userProfile = userProfileLookup.findByEmailOrThrow(email);
        UploadRequestDto request = new UploadRequestDto(userProfile.getId(), uploadRequestDto.fileName(),
                uploadRequestDto.contentType(), uploadRequestDto.size());
        UploadResponseDto response = mediaServiceClient.requestUploadUrl(request);
        metrics.getAvatarUploadRequested().increment();
        return response;
    }

    @Override
    public void changeUrlAvatar(String url, String userId) {
        log.info("Changing avatar URL to {} for userId: {}", url, userId);
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        userProfile.setAvatarUrl(url);
        userProfileRepository.save(userProfile);
        auditLogger.avatarChanged(userId, url);
    }
}
