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

    /**
     * Chỉ công khai với GET; mọi method khác vẫn phải có JWT hợp lệ.
     * <p>
     * Catalog phim cho khách chưa đăng nhập duyệt được, giống trang chủ của một dịch vụ xem phim.
     * Nhưng POST/PUT/PATCH/DELETE trên cùng đường dẫn đó là thao tác quản trị, nên phân biệt theo
     * method chứ không theo đường dẫn.
     * <p>
     * Lưu ý {@code /api/v1/movies/manage/**} KHÔNG nằm ở đây: nó là đường dẫn quản trị lồng bên
     * trong prefix công khai. Mẫu {@code /api/v1/movies/**} bên dưới vẫn khớp với nó, nên
     * movie-service phải tự chặn thêm một lớp nữa (xem {@code SecurityConfig} của service đó) —
     * gateway chỉ quyết định có cần JWT hay không, chứ không quyết định vai trò.
     */
    public static final List<String> PUBLIC_GET = List.of(
            "/api/v1/movies/**",
            "/api/v1/genres/**",
            "/api/v1/artists/**"
    );
}
