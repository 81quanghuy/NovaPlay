package vn.iotstar.utils.events;

import java.time.Instant;

public record EmailSent(String messageId, String to, String template, String status, String providerId, Instant ts) {}
