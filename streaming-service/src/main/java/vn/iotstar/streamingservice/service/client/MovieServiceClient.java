package vn.iotstar.streamingservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.iotstar.streamingservice.common.dto.ApiEnvelope;
import vn.iotstar.streamingservice.config.security.FeignSecurityConfig;
import vn.iotstar.streamingservice.service.client.dto.MovieDetailResponse;

/**
 * Đọc metadata phim — public read, relay danh tính người dùng THẬT (endpoint public ở phía
 * movie-service, nhưng vẫn quan trọng để log/audit đúng, và một số ngữ cảnh sau này có thể cần
 * roles thật ví dụ khi movie-service thêm endpoint riêng cho admin).
 */
@FeignClient(name = "movie-service", url = "${services.movie}", contextId = "MovieClientService",
        path = "/api/v1/movies", configuration = FeignSecurityConfig.class)
public interface MovieServiceClient {

    @GetMapping("/{idOrSlug}")
    ApiEnvelope<MovieDetailResponse> getPublishedMovie(@PathVariable String idOrSlug);
}
