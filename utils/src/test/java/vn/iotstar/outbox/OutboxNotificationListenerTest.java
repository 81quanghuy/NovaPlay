package vn.iotstar.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(MockitoExtension.class)
class OutboxNotificationListenerTest {

    @Mock private OutboxCatchUp catchUp;
    @Mock private OutboxRelayService relayService;

    @Test
    void khongKetNoiDuocThiThuLaiChuKhongChetHan() {
        OutboxProperties properties = new OutboxProperties();
        properties.setReconnectInitialDelay(Duration.ofMillis(20));
        properties.setReconnectMaxDelay(Duration.ofMillis(40));

        OutboxNotificationListener listener = new OutboxNotificationListener(
                "jdbc:postgresql://localhost:1/khong-ton-tai", "u", "p",
                properties, catchUp, relayService);

        listener.start();
        try {
            assertThat(listener.isRunning()).isTrue();
            // Vòng reconnect phải tiếp tục chạy dù mọi lần kết nối đều hỏng.
            await().atMost(Duration.ofSeconds(3))
                    .until(() -> listener.getFailedConnectionAttempts() >= 2);
        } finally {
            listener.stop();
        }

        assertThat(listener.isRunning()).isFalse();
    }

    @Test
    void stopPhaiTraVeNhanhDuChoDangNguSauBackoff() {
        // reconnectMaxDelay cố tình lớn (30s, giống mặc định prod) để mô phỏng đúng kịch bản của
        // Finding 1: nếu stop() không interrupt() được thread listener, join(5_000) sẽ timeout
        // và trả về muộn 5s mỗi lần pod tắt trong lúc Postgres đang down. Test này khẳng định
        // stop() trả về gần như ngay lập tức thay vì phải đợi hết join timeout.
        OutboxProperties properties = new OutboxProperties();
        properties.setReconnectInitialDelay(Duration.ofSeconds(30));
        properties.setReconnectMaxDelay(Duration.ofSeconds(30));

        OutboxNotificationListener listener = new OutboxNotificationListener(
                "jdbc:postgresql://localhost:1/khong-ton-tai", "u", "p",
                properties, catchUp, relayService);

        listener.start();
        // Đợi tới khi listener chắc chắn đang ở trong Thread.sleep(30_000) của lần backoff đầu
        // tiên (currentConnection == null lúc này vì connect đã thất bại).
        await().atMost(Duration.ofSeconds(3))
                .until(() -> listener.getFailedConnectionAttempts() >= 1);

        long start = System.nanoTime();
        listener.stop();
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(listener.isRunning()).isFalse();
        // Ngưỡng rộng rãi so với sleep 30s đang bị chặn — chỉ cần chứng minh interrupt() có tác
        // dụng, không phải join(5_000) timeout ra rồi mới trả về.
        assertThat(elapsedMillis).isLessThan(2_000);
    }
}
