package vn.iotstar.apigateway.configs;

public record RouteConfigItem( String path,
                               boolean useCircuitBreaker,
                               boolean useRetry,
                               boolean useRateLimiter) {
}
