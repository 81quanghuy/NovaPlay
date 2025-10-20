package vn.iotstar.utils.dto;

import java.util.Map;

public record EmailOtpRequested(
        String messageId, String userId, String email, Map<String, String> variables
) {}