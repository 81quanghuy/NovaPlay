package vn.iotstar.userservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.iotstar.userservice.common.dto.UploadRequestDto;
import vn.iotstar.userservice.common.dto.UploadResponseDto;

@FeignClient(name = "media-service", url = "${services.media}", contextId = "MediaClientService", path = "/api/v1/media")
public interface MediaServiceClient {

    @PostMapping("/upload/request")
    UploadResponseDto requestUploadUrl(@RequestBody UploadRequestDto payload);
}