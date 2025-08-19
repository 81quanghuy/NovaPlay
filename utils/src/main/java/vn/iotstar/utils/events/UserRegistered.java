package vn.iotstar.utils.events;

import java.time.Instant;

public record UserRegistered(String userId, String email, Instant createdAt) {}
