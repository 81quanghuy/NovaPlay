package vn.iotstar.notificationservice.common.dto;

import java.util.Map;

public record EmailOtpRequested(
        String messageId, String userId, String email, Map<String, String> variables
) {}