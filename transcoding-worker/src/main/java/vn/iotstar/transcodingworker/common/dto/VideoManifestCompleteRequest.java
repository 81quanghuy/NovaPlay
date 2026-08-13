package vn.iotstar.transcodingworker.common.dto;

import java.util.List;

public record VideoManifestCompleteRequest(
        String masterManifestS3Key,
        Integer durationSeconds,
        List<RenditionRequest> renditions,
        String wrappedKey
) {
}
