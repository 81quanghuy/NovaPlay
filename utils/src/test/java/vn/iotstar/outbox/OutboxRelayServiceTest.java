package vn.iotstar.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock private OutboxDao dao;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ScheduledExecutorService retryScheduler;
    @Mock private OutboxMetrics metrics;

    private OutboxRelayService relayService;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties();
        relayService = new OutboxRelayService(dao, kafkaTemplate, retryScheduler, metrics, properties);
    }

    private OutboxRecord record(int attempts, Instant nextAttemptAt) {
        return new OutboxRecord(id, "send-email.v1", "user-1", "{\"otp\":\"1\"}",
                attempts, nextAttemptAt);
    }

    @Test
    void nhanViecThatBaiThiKhongGuiKafka() {
        // Pod khác đã giành được row — đây là đường chạy bình thường khi có 2 replica,
        // không phải lỗi, nên tuyệt đối không được gửi trùng.
        when(dao.claimById(id)).thenReturn(Optional.empty());

        relayService.relay(id);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void guiThanhCongThiXoaRow() {
        when(dao.claimById(id)).thenReturn(Optional.of(record(1, Instant.now().plusSeconds(30))));
        when(kafkaTemplate.send("send-email.v1", "user-1", "{\"otp\":\"1\"}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        relayService.relay(id);

        verify(dao).delete(id);
        verify(metrics).published();
        verify(retryScheduler, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void guiHongVaConLuotThiGiuPendingVaHenThuLai() {
        Instant nextAttempt = Instant.now().plus(45, ChronoUnit.SECONDS);
        when(dao.claimById(id)).thenReturn(Optional.of(record(3, nextAttempt)));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("kafka sập")));

        relayService.relay(id);

        verify(dao).recordError(eq(id), contains("kafka sập"));
        verify(dao, never()).markFailed(any(), any());
        // Khoá chặt độ trễ hẹn lại: phải bám sát đúng nextAttemptAt mà claim đã tính (~45000ms),
        // không phải 0 hay một công thức backoff thứ hai được tính lại trong Java.
        ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);
        verify(retryScheduler).schedule(any(Runnable.class), delayCaptor.capture(), eq(TimeUnit.MILLISECONDS));
        assertThat(delayCaptor.getValue()).isCloseTo(45_000L, within(2_000L));
        verify(metrics).relayFailed();
    }

    @Test
    void loiVuotQuaGioiHanThiBiCatBotTruocKhiGhi() {
        String loiDai = "x".repeat(2500);
        when(dao.claimById(id)).thenReturn(Optional.of(record(3, Instant.now().plusSeconds(30))));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException(loiDai)));

        relayService.relay(id);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(dao).recordError(eq(id), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).hasSize(2000);
    }

    @Test
    void loiKhongCoMessageThiGhiChuoiNull() {
        when(dao.claimById(id)).thenReturn(Optional.of(record(3, Instant.now().plusSeconds(30))));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException()));

        relayService.relay(id);

        verify(dao).recordError(id, "null");
    }

    @Test
    void hetLuotThuThiChuyenSangFailedVaKhongHenLai() {
        when(dao.claimById(id)).thenReturn(Optional.of(record(10, Instant.now().plusSeconds(900))));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("kafka sập")));

        relayService.relay(id);

        verify(dao).markFailed(eq(id), contains("kafka sập"));
        verify(dao, never()).recordError(any(), any());
        verify(retryScheduler, never()).schedule(any(Runnable.class), anyLong(), any());
        verify(metrics).terminallyFailed();
    }

    @Test
    void sendKhongNhanViecLaiVaoLanNua() {
        // Đường catch-up đã nhận việc bằng chính câu UPDATE của claimBatch. Nếu send() gọi
        // claimById lần nữa thì attempts bị tăng hai lần cho cùng một lượt gửi.
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relayService.send(record(1, Instant.now().plusSeconds(30)));

        verify(dao, never()).claimById(any());
        verify(dao).delete(id);
    }
}
