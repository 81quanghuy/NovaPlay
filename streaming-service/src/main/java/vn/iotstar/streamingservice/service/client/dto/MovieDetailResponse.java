package vn.iotstar.streamingservice.service.client.dto;

import java.util.List;

/** Chỉ mang field streaming-service thực sự cần từ {@code MovieDetailDTO} của movie-service. */
public record MovieDetailResponse(
        String id,
        String slug,
        String title,
        String posterUrl,
        String mediaId,
        boolean series,
        String minPlan,
        List<EpisodeResponse> episodes
) {
}
