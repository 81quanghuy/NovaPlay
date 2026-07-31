package vn.iotstar.notificationservice.model.dto;

import lombok.Builder;
import vn.iotstar.notificationservice.model.enums.NotificationType;

import java.time.Instant;
import java.util.Map;

@Builder
public record NotificationDTO(
        String id,
        NotificationType type,
        String title,
        String body,
        Map<String, String> data,
        boolean read,
        Instant createdAt
) {}
