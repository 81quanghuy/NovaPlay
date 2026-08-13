package vn.iotstar.streamingservice.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WatchProgressRequest(
        Integer episodeNumber,
        @NotNull @Min(0) Integer positionSeconds,
        @Min(0) Integer durationSeconds
) {
}
