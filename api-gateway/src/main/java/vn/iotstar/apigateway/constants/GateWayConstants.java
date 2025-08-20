package vn.iotstar.apigateway.constants;

public class GateWayConstants {

    // Prefix for all API endpoints
    public static final String API_V1 = "/api/v1/";

    //circuitBreaker
    public static final String CIRCUIT_BREAKER = "circuitBreaker";

    // lb://
    public static final String LB = "lb://";

    //user-service
    public static final String USER_SERVICE = "user-service";

    //movie-service
    public static final String MOVIE_SERVICE = "movie-service";

    // auth-service
    public static final String AUTH_SERVICE = "auth-service";

    // Header for correlation ID
    public static final String CORRELATION_ID = "novaPlay-correlation-id";

    // X-Response-Time
    public static final String X_RESPONSE_TIME = "X-Response-Time";

    // Fallback URI for circuit breaker
    public static final String FALLBACK_URI = "forward:/contactSupport";

    // Message for contact support
    public static final String CONTACT_SUPPORT_MESSAGE = "An error occurred. Please try after some time or contact support team!!!";

    // PATCH SETTING SPRING SECURITY
    public static final String USER_SERVICE_PATH = "novaplay/user-service/**";

    public static final String MOVIE_SERVICE_PATH = "novaplay/movie-service/**";

}