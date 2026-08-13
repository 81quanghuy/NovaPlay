package vn.iotstar.userservice.service;

import vn.iotstar.userservice.model.dto.UpdateUserProfileRequest;
import vn.iotstar.userservice.model.dto.UserProfileDTO;
import vn.iotstar.userservice.common.dto.UploadRequestDto;
import vn.iotstar.userservice.common.dto.UploadResponseDto;
import vn.iotstar.userservice.common.dto.UserRegister;
import vn.iotstar.userservice.util.Plan;

public interface UserProfileService {

    void registerNewUser(UserRegister evt);

    UserProfileDTO getProfile(String email);

    UserProfileDTO updateProfile(String email, UpdateUserProfileRequest request);

    /** [ADMIN] Đường duy nhất để đổi gói cước — người dùng không tự đặt được qua updateProfile. */
    UserProfileDTO updatePlan(String email, Plan plan);

    UploadResponseDto changeAvatar(UploadRequestDto request, String email, String traceId);

    void changeUrlAvatar(String url, String userId);
}
