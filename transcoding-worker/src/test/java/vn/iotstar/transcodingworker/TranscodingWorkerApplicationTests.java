package vn.iotstar.transcodingworker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class TranscodingWorkerApplicationTests {

    @Test
    @DisplayName("lớp application được cấu hình như một Spring Boot application")
    void isAnnotatedAsSpringBootApplication() {
        assertThat(TranscodingWorkerApplication.class.isAnnotationPresent(SpringBootApplication.class)).isTrue();
    }

}
