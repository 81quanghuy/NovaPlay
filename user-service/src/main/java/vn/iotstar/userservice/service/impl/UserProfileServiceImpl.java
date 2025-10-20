package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.userservice.model.entity.UserProfile;
import vn.iotstar.userservice.repository.IUserProfileRepository;
import vn.iotstar.userservice.service.UserProfileService;
import vn.iotstar.userservice.service.client.MediaServiceClient;
import vn.iotstar.userservice.util.Constants;
import vn.iotstar.userservice.util.Plan;
import vn.iotstar.utils.dto.UploadRequestDto;
import vn.iotstar.utils.dto.UploadResponseDto;
import vn.iotstar.utils.dto.UserRegister;

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
    public UploadResponseDto changeAvatar(UploadRequestDto uploadRequestDto, String email, String traceId) {
        log.info("Changing avatar to {} for userId: {} | traceId: {}",uploadRequestDto, email, traceId);
        UserProfile userProfile = userProfileRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UploadRequestDto request = new UploadRequestDto(userProfile.getId(), uploadRequestDto.fileName(),
                uploadRequestDto.contentType(), uploadRequestDto.size());
        return mediaServiceClient.requestUploadUrl(request);
    }

    @Override
    @Transactional
    public void changeUrlAvatar(String url, String userId) {
        log.info("Changing avatar URL to {} for userId: {}", url, userId);
        UserProfile userProfile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        userProfile.setAvatarUrl(url);
        userProfileRepository.save(userProfile);
    }
}
