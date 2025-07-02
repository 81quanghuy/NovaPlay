package vn.iotstar.userservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.userservice.model.dto.WatchHistoryRequest;
import vn.iotstar.userservice.model.dto.GetWatchHistoryRequest;
import vn.iotstar.userservice.service.impl.WatchHistoryService;

@RestController
@Slf4j
@RequiredArgsConstructor
public class WatchHistoryController {

    private final WatchHistoryService watchHistoryService;

    /**
     * Add watch history for a user
     * @param pWatchHistoryRequest
     * @return
     */
    @PostMapping("/watch-history/add")
    public ResponseEntity<GenericResponse> addWatchHistory(@Valid @RequestBody WatchHistoryRequest pWatchHistoryRequest) {
        log.info("Adding watch history for user: {}", pWatchHistoryRequest.getUserId());
        return watchHistoryService.addWatchHistory(pWatchHistoryRequest);
    }

    /**
     * Get watch history for a user
     * @param pWatchHistoryRequest
     * @return
     */
    @GetMapping("/watch-history/get")
    public ResponseEntity<GenericResponse> getWatchHistory(@Valid @RequestBody GetWatchHistoryRequest pWatchHistoryRequest) {
        log.info("Getting watch history for user: {}", pWatchHistoryRequest.getUserId());
        return watchHistoryService.getWatchHistory(pWatchHistoryRequest);
    }

    // get list all watch history for a user
    /**
     * Get all watch history for a user
     * @param pWatchHistoryRequest
     * @return
     */
    @GetMapping("/watch-history/list")
    public ResponseEntity<GenericResponse> getAllWatchHistory(@Valid @RequestBody GetWatchHistoryRequest pWatchHistoryRequest) {
        log.info("Getting all watch history for user: {}", pWatchHistoryRequest.getUserId());
        return watchHistoryService.getAllWatchHistory(pWatchHistoryRequest);
    }
    // get phan trang watch history for a user
    /**
     * Get paginated watch history for a user
     * @param pWatchHistoryRequest
     * @return
     */
    @GetMapping("/watch-history/paginated")
    public ResponseEntity<GenericResponse> getPaginatedWatchHistory(@Valid @RequestBody GetWatchHistoryRequest pWatchHistoryRequest) {
        log.info("Getting paginated watch history for user: {}", pWatchHistoryRequest.getUserId());
        return watchHistoryService.getPaginatedWatchHistory(pWatchHistoryRequest);
    }
    /**
     * Delete watch history for a user
     * @param pWatchHistoryRequest
     * @return
     */
    @DeleteMapping("/watch-history/delete")
    public ResponseEntity<GenericResponse> deleteWatchHistory(@Valid @RequestBody WatchHistoryRequest pWatchHistoryRequest) {
        log.info("Deleting watch history for user: {}", pWatchHistoryRequest.getUserId());
        return watchHistoryService.deleteWatchHistory(pWatchHistoryRequest);
    }

}
