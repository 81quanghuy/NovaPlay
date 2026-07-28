package vn.iotstar.apigateway.constants;

public class GateWayConstants {

    private GateWayConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static final String API_V1 = "/api/v1/";
    public static final String LB = "lb://";
    public static final String USER_SERVICE = "user-service";
    public static final String MOVIE_SERVICE = "movie-service";
    public static final String AUTH_SERVICE = "auth-service";
    public static final String CORRELATION_ID = "novaPlay-correlation-id";
    public static final String X_RESPONSE_TIME = "X-Response-Time";
    public static final String FALLBACK_URI = "forward:/fallback/message";
    public static final String CONTACT_SUPPORT_MESSAGE = "An error occurred. Please try after some time or contact support team!!!";
    public static final String X_USER_EMAIL = "X-User-Email";
    public static final String X_USER_ROLES = "X-User-Roles";
    /** Chứng minh request đi qua gateway, để downstream không tin header danh tính từ client. */
    public static final String X_GATEWAY_AUTH = "X-Gateway-Auth";
}
