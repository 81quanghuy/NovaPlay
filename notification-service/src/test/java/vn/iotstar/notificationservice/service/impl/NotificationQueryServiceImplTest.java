package vn.iotstar.notificationservice.service.impl;

import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import vn.iotstar.notificationservice.mapper.NotificationMapper;
import vn.iotstar.notificationservice.model.entity.Notification;
import vn.iotstar.notificationservice.repository.NotificationRepository;
import vn.iotstar.utils.exceptions.wrapper.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceImplTest {

    private static final String ALICE = "alice@novaplay.vn";

    @Mock private NotificationRepository repository;
    @Mock private MongoTemplate mongoTemplate;

    private NotificationQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationQueryServiceImpl(repository, mongoTemplate, new NotificationMapper());
    }

    @Test
    void unread_only_dung_truy_van_rieng() {
        when(repository.findByUserEmailAndReadAtIsNull(eq(ALICE), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(ALICE, Pageable.ofSize(20), true);

        verify(repository).findByUserEmailAndReadAtIsNull(eq(ALICE), any());
        verify(repository, never()).findByUserEmail(any(), any());
    }

    @Test
    @DisplayName("markRead lọc theo cả id lẫn userEmail — không bao giờ chỉ theo id")
    void mark_read_loc_theo_ca_id_lan_email() {
        when(mongoTemplate.updateFirst(any(Query.class), any(), eq(Notification.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        Notification updated = Notification.builder().id("n-1").userEmail(ALICE).build();
        when(repository.findById("n-1")).thenReturn(Optional.of(updated));

        service.markRead(ALICE, "n-1");

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateFirst(captor.capture(), any(), eq(Notification.class));
        String query = captor.getValue().getQueryObject().toJson();
        assertThat(query).contains("n-1").contains(ALICE);
    }

    @Test
    @DisplayName("không khớp bản ghi nào thì 404 — dùng chung một phản hồi cho 'không tồn tại' và 'của người khác'")
    void khong_khop_thi_404() {
        when(mongoTemplate.updateFirst(any(Query.class), any(), eq(Notification.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThatThrownBy(() -> service.markRead(ALICE, "cua-nguoi-khac"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).findById(any());
    }

    @Test
    void mark_all_read_chi_cham_ban_ghi_chua_doc_cua_dung_nguoi() {
        when(mongoTemplate.updateMulti(any(Query.class), any(), eq(Notification.class)))
                .thenReturn(UpdateResult.acknowledged(4, 4L, null));

        assertThat(service.markAllRead(ALICE)).isEqualTo(4);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateMulti(captor.capture(), any(), eq(Notification.class));
        String query = captor.getValue().getQueryObject().toJson();
        assertThat(query).contains(ALICE).contains("read_at");
    }
}
