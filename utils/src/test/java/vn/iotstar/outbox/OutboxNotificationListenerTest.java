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
}
