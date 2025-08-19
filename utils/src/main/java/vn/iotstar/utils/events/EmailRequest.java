package vn.iotstar.utils.events;

import java.util.Map;

public record EmailRequest(String messageId, String to, String subject, String template, Map<String, Object> variables) {}
