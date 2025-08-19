package vn.iotstar.utils.events;

import java.util.Map;

public record PushRequest(String requestId, String userId, String channel, String template, Map<String, Object> variables) {}
