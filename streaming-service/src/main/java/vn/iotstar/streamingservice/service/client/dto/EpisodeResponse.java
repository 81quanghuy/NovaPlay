package vn.iotstar.streamingservice.service.client.dto;

public record EpisodeResponse(Integer episodeNumber, String title, String mediaId, Integer durationInMinutes) {
}
