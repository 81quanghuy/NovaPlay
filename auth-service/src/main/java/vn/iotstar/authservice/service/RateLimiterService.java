package vn.iotstar.authservice.service;

import vn.iotstar.utils.exceptions.wrapper.TooManyRequestsException;

import java.time.Duration;

public interface RateLimiterService {

    void checkAndIncrement(String key, int max, Duration window) throws TooManyRequestsException;

    void reset(String key);

    long getCurrentCount(String key);
}
