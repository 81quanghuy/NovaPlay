package vn.iotstar.apigateway.constants;

import java.util.List;

public final class PublicEndpoints {
    private PublicEndpoints() {}

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
}
