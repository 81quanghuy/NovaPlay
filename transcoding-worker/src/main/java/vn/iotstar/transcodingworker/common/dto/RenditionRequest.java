package vn.iotstar.transcodingworker.common.dto;

public record RenditionRequest(
        String resolutionLabel,
        Integer width,
        Integer height,
        Integer bitrateKbps,
        String playlistS3Key
) {
}
