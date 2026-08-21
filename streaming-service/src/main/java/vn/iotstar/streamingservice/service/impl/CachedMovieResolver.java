package vn.iotstar.streamingservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import vn.iotstar.streamingservice.exception.ResourceNotFoundException;
import vn.iotstar.streamingservice.service.client.MovieServiceClient;
import vn.iotstar.streamingservice.service.client.dto.MovieDetailResponse;
import vn.iotstar.streamingservice.utils.CacheNames;

/**
 * Tách riêng thành bean của nó, cùng lý do với {@link CachedManifestResolver}: {@code @Cacheable}
 * dựa trên AOP proxy, tự gọi method public cùng lớp qua {@code this.xxx()} sẽ bỏ qua proxy và
 * không cache được gì cả.
 */
@Component
@RequiredArgsConstructor
public class CachedMovieResolver {

    private final MovieServiceClient movieServiceClient;

    @Cacheable(value = CacheNames.MOVIE_RESOLUTION, key = "#idOrSlug")
    public MovieDetailResponse resolveByIdOrSlug(String idOrSlug) {
        MovieDetailResponse movie = movieServiceClient.getPublishedMovie(idOrSlug).result();
        if (movie == null) {
            throw new ResourceNotFoundException("Movie not found: " + idOrSlug);
        }
        return movie;
    }
}
