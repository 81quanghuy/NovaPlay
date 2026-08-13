package vn.iotstar.streamingservice.service.client.dto;

public record RenditionResponse(
        String resolutionLabel, Integer width, Integer height, Integer bitrateKbps, String playlistS3Key
) {
}
