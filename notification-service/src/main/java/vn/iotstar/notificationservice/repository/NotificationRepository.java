package vn.iotstar.notificationservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import vn.iotstar.notificationservice.model.entity.Notification;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    Page<Notification> findByUserEmail(String userEmail, Pageable pageable);

    Page<Notification> findByUserEmailAndReadAtIsNull(String userEmail, Pageable pageable);

    long countByUserEmailAndReadAtIsNull(String userEmail);
}
