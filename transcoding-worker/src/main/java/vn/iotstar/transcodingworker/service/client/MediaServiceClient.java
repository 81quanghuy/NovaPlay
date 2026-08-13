package vn.iotstar.transcodingworker.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.iotstar.transcodingworker.common.dto.ApiEnvelope;
import vn.iotstar.transcodingworker.common.dto.VideoManifestCompleteRequest;
import vn.iotstar.transcodingworker.common.dto.VideoManifestDto;
import vn.iotstar.transcodingworker.common.dto.VideoManifestFailRequest;
import vn.iotstar.transcodingworker.config.security.ServiceIdentityFeignConfig;

@FeignClient(name = "media-service", url = "${services.media}", contextId = "MediaClientService",
        path = "/api/v1/media/video-manifests", configuration = ServiceIdentityFeignConfig.class)
public interface MediaServiceClient {

    @GetMapping("/{id}")
    ApiEnvelope<VideoManifestDto> getById(@PathVariable String id);

    @PatchMapping("/{id}/processing")
    ApiEnvelope<VideoManifestDto> markProcessing(@PathVariable String id);

    @PatchMapping("/{id}/complete")
    ApiEnvelope<VideoManifestDto> markComplete(@PathVariable String id, @RequestBody VideoManifestCompleteRequest request);

    @PatchMapping("/{id}/fail")
    ApiEnvelope<VideoManifestDto> markFailed(@PathVariable String id, @RequestBody VideoManifestFailRequest request);
}
