package vn.iotstar.userservice.service;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import vn.iostar.utils.constants.GenericResponse;
import vn.iotstar.userservice.model.dto.WatchHistoryRequest;
import vn.iotstar.userservice.model.dto.GetWatchHistoryRequest;

public interface IWatchHistoryService {
    ResponseEntity<GenericResponse> addWatchHistory(@Valid WatchHistoryRequest pWatchHistoryRequest);

    ResponseEntity<GenericResponse> getWatchHistory(@Valid GetWatchHistoryRequest pWatchHistoryRequest);

    ResponseEntity<GenericResponse> deleteWatchHistory(@Valid WatchHistoryRequest pWatchHistoryRequest);

    ResponseEntity<GenericResponse> getAllWatchHistory(@Valid GetWatchHistoryRequest pWatchHistoryRequest);

    ResponseEntity<GenericResponse> getPaginatedWatchHistory(@Valid GetWatchHistoryRequest pWatchHistoryRequest);
}
