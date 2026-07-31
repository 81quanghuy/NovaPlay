package vn.iotstar.notificationservice.mapper;

import org.springframework.stereotype.Component;
import vn.iotstar.notificationservice.model.dto.NotificationDTO;
import vn.iotstar.notificationservice.model.entity.Notification;

@Component
public class NotificationMapper {

    public NotificationDTO toDto(Notification entity) {
        return NotificationDTO.builder()
                .id(entity.getId())
                .type(entity.getType())
                .title(entity.getTitle())
                .body(entity.getBody())
                .data(entity.getData())
                .read(entity.getReadAt() != null)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
