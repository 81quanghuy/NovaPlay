package vn.iotstar.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPropertiesTest {

    @Test
    void giaTriMacDinhPhaiAnToanKhiKhongCauHinhGi() {
        OutboxProperties props = new OutboxProperties();

        assertThat(props.getChannel()).isEqualTo("outbox_new");
        assertThat(props.getMaxAttempts()).isEqualTo(10);
        assertThat(props.getBatchSize()).isEqualTo(100);
        assertThat(props.getListenTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(props.getReconnectInitialDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(props.getReconnectMaxDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(props.isPendingGaugeEnabled()).isTrue();
    }
}
