package vn.iotstar.streamingservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Playback token ("pt") thiếu, hết hạn, sai chữ ký, hoặc không khớp manifest đang truy cập.
 * Endpoint {@code /api/v1/streaming/hls/**} là {@code permitAll} ở tầng Spring Security (xem
 * SecurityConfig) — đây là lớp bảo vệ thực sự cho nội dung video, không phải Spring Security.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedPlaybackException extends RuntimeException {
    public UnauthorizedPlaybackException(String message) {
        super(message);
    }
}
