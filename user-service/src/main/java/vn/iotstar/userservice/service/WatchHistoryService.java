package vn.iotstar.userservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.iotstar.userservice.model.dto.UpsertWatchProgressRequest;
import vn.iotstar.userservice.model.entity.WatchProgress;

import java.util.Optional;

public interface WatchHistoryService {

    WatchProgress upsertWatchProgress(String email, UpsertWatchProgressRequest request);

    Page<WatchProgress> getRecentWatchProgress(String email, Pageable pageable);

    Optional<WatchProgress> getWatchProgress(String email, String movieId);
}
