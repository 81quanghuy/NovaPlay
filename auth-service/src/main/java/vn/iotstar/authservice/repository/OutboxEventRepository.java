package vn.iotstar.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.iotstar.authservice.model.entity.OutboxEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status);
}
