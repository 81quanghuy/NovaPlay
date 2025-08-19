package vn.iotstar.utils.events;

import java.time.Instant;

public record PushDelivered(String requestId, String userId, String channel, String status, Instant ts) {}
