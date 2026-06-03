package vn.iotstar.userservice.service;

import vn.iotstar.userservice.model.dto.UpdateUserProfileRequest;
import vn.iotstar.userservice.model.dto.UserProfileDTO;
import vn.iotstar.utils.dto.UploadRequestDto;
import vn.iotstar.utils.dto.UploadResponseDto;
import vn.iotstar.utils.dto.UserRegister;

public interface UserProfileService {

    void registerNewUser(UserRegister evt);

    UserProfileDTO getProfile(String email);

    UserProfileDTO updateProfile(String email, UpdateUserProfileRequest request);

    UploadResponseDto changeAvatar(UploadRequestDto request, String email, String traceId);

    void changeUrlAvatar(String url, String userId);
}
