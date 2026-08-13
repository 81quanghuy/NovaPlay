package vn.iotstar.apigateway.constants;

import java.util.List;

public final class PublicEndpoints {
    private PublicEndpoints() {}

    /** Công khai với mọi HTTP method. */
    public static final List<String> ALL = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/resend-registration-otp",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/refresh-token",
            "/fallback/**",
            "/swagger/**",
            "/actuator/**",
            "/v3/api-docs/**",
            "/webjars/**"
    );

    public static final List<String> PUBLIC_GET = List.of(
            "/api/v1/movies/**",
            "/api/v1/genres/**",
            "/api/v1/artists/**",
            /*
             * Trình phát HLS (đặc biệt engine gốc của Safari/iOS) không tự gắn được header
             * Authorization vào từng request tải playlist/segment. Bảo vệ nội dung nằm ở playback
             * token ngắn hạn (query param "pt") streaming-service tự validate — xem
             * StreamingHlsController/SecurityConfig bên streaming-service.
             */
            "/api/v1/streaming/hls/**"
    );
}
