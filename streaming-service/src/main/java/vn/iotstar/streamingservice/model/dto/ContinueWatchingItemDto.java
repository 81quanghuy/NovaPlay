package vn.iotstar.streamingservice.model.dto;

import java.time.Instant;

public record ContinueWatchingItemDto(
        String movieId,
        Integer episodeNumber,
        int positionSeconds,
        int durationSeconds,
        Instant updatedAt
) {
}
