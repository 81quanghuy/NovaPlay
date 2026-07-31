package vn.iotstar.notificationservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import vn.iotstar.notificationservice.mapper.NotificationMapper;
import vn.iotstar.notificationservice.model.dto.NotificationDTO;
import vn.iotstar.notificationservice.model.entity.Notification;
import vn.iotstar.notificationservice.repository.NotificationRepository;
import vn.iotstar.notificationservice.service.NotificationQueryService;
import vn.iotstar.utils.exceptions.wrapper.ResourceNotFoundException;

import java.time.Instant;

import static vn.iotstar.notificationservice.util.Constants.ID;
import static vn.iotstar.notificationservice.util.Constants.READ_AT;
import static vn.iotstar.notificationservice.util.Constants.USER_EMAIL;

@Service
@RequiredArgsConstructor
public class NotificationQueryServiceImpl implements NotificationQueryService {

    private final NotificationRepository repository;
    private final MongoTemplate mongoTemplate;
    private final NotificationMapper mapper;

    @Override
    public Page<NotificationDTO> list(String userEmail, Pageable pageable, boolean unreadOnly) {
        Page<Notification> page = unreadOnly
                ? repository.findByUserEmailAndReadAtIsNull(userEmail, pageable)
                : repository.findByUserEmail(userEmail, pageable);
        return page.map(mapper::toDto);
    }

    @Override
    public long unreadCount(String userEmail) {
        return repository.countByUserEmailAndReadAtIsNull(userEmail);
    }

    @Override
    public NotificationDTO markRead(String userEmail, String id) {
        Query query = Query.query(Criteria.where(ID).is(id).and(USER_EMAIL).is(userEmail));
        Update update = Update.update(READ_AT, Instant.now());
        // updateFirst thay vì findById+save: một round-trip, và tránh race giữa hai request
        // markRead đồng thời trên cùng một document.
        var result = mongoTemplate.updateFirst(query, update, Notification.class);
        if (result.getMatchedCount() == 0) {
            // Cùng một 404 cho "không tồn tại" lẫn "thuộc về người khác" — không tiết lộ sự tồn tại
            // của thông báo người khác qua sự khác biệt giữa 404 và 403.
            throw new ResourceNotFoundException("Không tìm thấy thông báo");
        }
        Notification updated = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo"));
        return mapper.toDto(updated);
    }

    @Override
    public long markAllRead(String userEmail) {
        Query query = Query.query(Criteria.where(USER_EMAIL).is(userEmail).and(READ_AT).isNull());
        Update update = Update.update(READ_AT, Instant.now());
        return mongoTemplate.updateMulti(query, update, Notification.class).getModifiedCount();
    }
}
