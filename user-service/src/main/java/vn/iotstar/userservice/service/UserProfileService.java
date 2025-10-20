package vn.iotstar.userservice.service;

import vn.iotstar.utils.dto.UploadRequestDto;
import vn.iotstar.utils.dto.UploadResponseDto;
import vn.iotstar.utils.dto.UserRegister;

public interface UserProfileService {

    /**
     * Register new user profile when receive event from auth service
     * @param evt event data
     */
    void registerNewUser(UserRegister evt);

    /**
     * Change avatar of user
     * @param request request data
     * @param email email of user
     * @param traceId trace id for logging
     */
    UploadResponseDto changeAvatar(UploadRequestDto request, String email, String traceId);

    /**
     * Change URL of avatar when receive event from media service
     * @param url new URL of avatar
     * @param userId id of user
     */
    void changeUrlAvatar(String url, String userId);
}
