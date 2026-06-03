package vn.iotstar.userservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.iotstar.userservice.model.dto.UpsertWatchProgressRequest;
import vn.iotstar.userservice.model.entity.WatchProgress;
import vn.iotstar.userservice.service.WatchHistoryService;
import vn.iotstar.utils.constants.GenericResponse;

@RestController
@RequestMapping("/api/v1/users/watch-progress")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Watch Progress API", description = "Theo dõi tiến độ xem — hỗ trợ 'Tiếp tục xem'")
public class WatchHistoryController {

    private final WatchHistoryService watchHistoryService;

    @Operation(summary = "Cập nhật tiến độ xem (upsert)", security = @SecurityRequirement(name = "bearer-jwt"))
    @PutMapping
    public ResponseEntity<GenericResponse> upsertWatchProgress(
            @RequestHeader("X-User-Email") String email,
            @Valid @RequestBody UpsertWatchProgressRequest request) {
        WatchProgress progress = watchHistoryService.upsertWatchProgress(email, request);
        return ResponseEntity.ok(GenericResponse.success(progress, "Watch progress updated"));
    }

    @Operation(summary = "Lấy danh sách 'Tiếp tục xem' (sort by lastWatchedAt)", security = @SecurityRequirement(name = "bearer-jwt"))
    @GetMapping
    public ResponseEntity<GenericResponse> getRecentWatchProgress(
            @RequestHeader("X-User-Email") String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<WatchProgress> result = watchHistoryService.getRecentWatchProgress(email, PageRequest.of(page, size));
        return ResponseEntity.ok(GenericResponse.success(result, "Watch progress retrieved"));
    }

    @Operation(summary = "Lấy vị trí resume cho một nội dung cụ thể", security = @SecurityRequirement(name = "bearer-jwt"))
    @GetMapping("/{movieId}")
    public ResponseEntity<GenericResponse> getWatchProgress(
            @RequestHeader("X-User-Email") String email,
            @PathVariable String movieId) {
        WatchProgress progress = watchHistoryService.getWatchProgress(email, movieId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No watch progress for: " + movieId));
        return ResponseEntity.ok(GenericResponse.success(progress, "Watch progress retrieved"));
    }
}
