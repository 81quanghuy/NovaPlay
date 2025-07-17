package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.userservice.model.dto.WatchHistoryRequest;
import vn.iotstar.userservice.model.dto.GetWatchHistoryRequest;
import vn.iotstar.userservice.model.entity.WatchHistory;
import vn.iotstar.userservice.repository.IWatchHistoryRepository;
import vn.iotstar.userservice.service.IWatchHistoryService;

import static vn.iotstar.userservice.util.MessageProperties.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class WatchHistoryService implements IWatchHistoryService {
    private final IWatchHistoryRepository watchHistoryRepository;

    /**
     * Adds a watch history entry for a user.
     *
     * @param pWatchHistoryRequest The request containing watch history details.
     * @return ResponseEntity with GenericResponse indicating success or failure.
     */
    @Override
    public ResponseEntity<GenericResponse> addWatchHistory(WatchHistoryRequest pWatchHistoryRequest) {
        log.info("Adding watch history for user: {}", pWatchHistoryRequest.getUserId());
        // Check if the watch history already exists
        Boolean userExists = watchHistoryRepository.existsByUserIdAndMovieId(
                pWatchHistoryRequest.getUserId(), pWatchHistoryRequest.getMovieId());
        if (userExists) {
            log.error("User with user ID {} already exists", pWatchHistoryRequest.getUserId());
            return ResponseEntity.badRequest().body(GenericResponse.builder()
                    .success(false)
                    .message(WATCH_HISTORY_ALREADY_EXISTS)
                    .statusCode(400)
                    .build());
        }
        WatchHistory watchHistory = WatchHistory.builder()
                .userId(pWatchHistoryRequest.getUserId())
                .movieId(pWatchHistoryRequest.getMovieId())
                .watchedAt(pWatchHistoryRequest.getWatchedAt())
                .totalWatchTime(pWatchHistoryRequest.getTotalWatchTime())
                .build();
        watchHistoryRepository.save(watchHistory);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(WATCH_HISTORY_CREATE_SUCCESS)
                .result(watchHistory)
                .statusCode(200)
                .build());
    }

    @Override
    public ResponseEntity<GenericResponse> getWatchHistory(GetWatchHistoryRequest pWatchHistoryRequest) {
        log.info("Retrieving watch history for user: {}", pWatchHistoryRequest.getUserId());
        WatchHistory watchHistory = watchHistoryRepository.findByUserIdAndMovieId(
                pWatchHistoryRequest.getUserId(), pWatchHistoryRequest.getMovieId());

        if (watchHistory == null) {
            return ResponseEntity.ok(GenericResponse.builder()
                    .success(false)
                    .message(WATCH_HISTORY_NOT_FOUND)
                    .statusCode(404)
                    .build());
        }

        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(WATCH_HISTORY_RETRIEVE_SUCCESS)
                .result(watchHistory)
                .statusCode(200)
                .build());
    }

    @Override
    public ResponseEntity<GenericResponse> deleteWatchHistory(WatchHistoryRequest pWatchHistoryRequest) {
        log.info("Deleting watch history for user: {}", pWatchHistoryRequest.getUserId());
        WatchHistory watchHistory = watchHistoryRepository.findByUserIdAndMovieId(
                pWatchHistoryRequest.getUserId(), pWatchHistoryRequest.getMovieId());

        if (watchHistory == null) {
            return ResponseEntity.ok(GenericResponse.builder()
                    .success(false)
                    .message(WATCH_HISTORY_NOT_FOUND)
                    .statusCode(404)
                    .build());
        }

        watchHistoryRepository.delete(watchHistory);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(WATCH_HISTORY_DELETE_SUCCESS)
                .statusCode(200)
                .build());
    }

    /**
     * Retrieves all watch history entries for a user.
     *
     * @param pWatchHistoryRequest The request containing user ID.
     * @return ResponseEntity with GenericResponse containing the list of watch history entries.
     */
    @Override
    public ResponseEntity<GenericResponse> getAllWatchHistory(GetWatchHistoryRequest pWatchHistoryRequest) {
        log.info("Retrieving all watch history for user: {}", pWatchHistoryRequest.getUserId());
        var watchHistoryList = watchHistoryRepository.findAllByUserId(pWatchHistoryRequest.getUserId());

        if (watchHistoryList.isEmpty()) {
            return ResponseEntity.ok(GenericResponse.builder()
                    .success(false)
                    .message(WATCH_HISTORY_NOT_FOUND)
                    .statusCode(404)
                    .build());
        }

        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(WATCH_HISTORY_RETRIEVE_SUCCESS)
                .result(watchHistoryList)
                .statusCode(200)
                .build());
    }

    /**
     * Retrieves paginated watch history entries for a user.
     *
     * @param pWatchHistoryRequest The request containing user ID and pagination details.
     * @return ResponseEntity with GenericResponse containing the paginated list of watch history entries.
     */
    @Override
    public ResponseEntity<GenericResponse> getPaginatedWatchHistory(GetWatchHistoryRequest pWatchHistoryRequest) {
        log.info("Retrieving paginated watch history for user: {}", pWatchHistoryRequest.getUserId());

        Pageable pageable = Pageable.ofSize(pWatchHistoryRequest.getPageSize())
                .withPage(pWatchHistoryRequest.getPageNumber());
        Page<WatchHistory> watchHistoryPage = watchHistoryRepository.findByUserId(
                pWatchHistoryRequest.getUserId(),pageable);

        if (watchHistoryPage.isEmpty()) {
            return ResponseEntity.ok(GenericResponse.builder()
                    .success(false)
                    .message(WATCH_HISTORY_NOT_FOUND)
                    .statusCode(404)
                    .build());
        }

        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message(WATCH_HISTORY_RETRIEVE_SUCCESS)
                .result(watchHistoryPage)
                .statusCode(200)
                .build());
    }

}
