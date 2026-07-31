package vn.iotstar.notificationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.notificationservice.service.NotificationQueryService;
import vn.iotstar.utils.constants.GenericResponse;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Thông báo in-app của người dùng đang đăng nhập.")
public class NotificationController {

    private final NotificationQueryService queryService;

    @GetMapping
    @Operation(summary = "Danh sách thông báo của người dùng hiện tại, có phân trang")
    public ResponseEntity<GenericResponse> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {

        Pageable pageable = PageableFactory.of(page, size, "createdAt", "desc",
                PageableFactory.NOTIFICATION_SORT_FIELDS, "createdAt");
        return ResponseEntity.ok(GenericResponse.success(
                queryService.list(authentication.getName(), pageable, unreadOnly)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Số thông báo chưa đọc của người dùng hiện tại")
    public ResponseEntity<GenericResponse> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(GenericResponse.success(
                java.util.Map.of("unreadCount", queryService.unreadCount(authentication.getName()))));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Đánh dấu một thông báo đã đọc")
    public ResponseEntity<GenericResponse> markRead(Authentication authentication, @PathVariable String id) {
        return ResponseEntity.ok(GenericResponse.success(
                queryService.markRead(authentication.getName(), id), "Đã đánh dấu đã đọc"));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Đánh dấu toàn bộ thông báo đã đọc")
    public ResponseEntity<GenericResponse> markAllRead(Authentication authentication) {
        long updated = queryService.markAllRead(authentication.getName());
        return ResponseEntity.ok(GenericResponse.success(
                java.util.Map.of("updated", updated), "Đã đánh dấu " + updated + " thông báo là đã đọc"));
    }
}
