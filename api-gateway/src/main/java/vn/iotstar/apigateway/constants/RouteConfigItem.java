package vn.iotstar.apigateway.constants;

public record RouteConfigItem( String path,
                               boolean useCircuitBreaker,
                               boolean useRetry,
                               boolean useRateLimiter) {
}