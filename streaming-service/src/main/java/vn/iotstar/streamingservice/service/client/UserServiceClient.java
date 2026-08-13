package vn.iotstar.streamingservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import vn.iotstar.streamingservice.common.dto.ApiEnvelope;
import vn.iotstar.streamingservice.config.security.FeignSecurityConfig;
import vn.iotstar.streamingservice.service.client.dto.UserProfileResponse;

/** Đọc gói cước của CHÍNH người đang gọi — relay danh tính thật, không phải {@code ROLE_SERVICE}. */
@FeignClient(name = "user-service", url = "${services.user}", contextId = "UserClientServiceStreaming",
        path = "/api/v1/users", configuration = FeignSecurityConfig.class)
public interface UserServiceClient {

    @GetMapping("/me")
    ApiEnvelope<UserProfileResponse> getMyProfile();
}
