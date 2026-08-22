package vn.iotstar.notificationservice.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.iotstar.notificationservice.config.MongoIndexInitializer;
import vn.iotstar.notificationservice.config.security.MongoAuditingConfig;
import vn.iotstar.notificationservice.model.entity.Notification;
import vn.iotstar.notificationservice.model.enums.NotificationType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.iotstar.notificationservice.util.Constants.*;

/**
 * Integration test chạy trên MongoDB thật.
 * <p>
 * Những thứ kiểm ở đây đều là hành vi của MongoDB chứ không phải của code Java: unique index ngầm
 * trên {@code _id} có thật sự chặn ghi trùng không, partial index và TTL index có được tạo đúng
 * không, và {@code MongoIndexInitializer} chạy lần thứ hai có ăn lỗi 85 IndexOptionsConflict không.
 * Mock không chứng minh được gì trong số đó.
 */
@Testcontainers
@DataMongoTest
// @DataMongoTest chỉ scan repository/document, không kéo theo @Configuration của ứng dụng —
// thiếu import này thì auditorAware không được đăng ký, @EnableMongoAuditing không kích hoạt,
// và @CreatedDate im lặng bị bỏ qua dù @Version đã đúng.
@Import(MongoAuditingConfig.class)
@EnabledIf("dockerAvailable")
class NotificationRepositoryIT {

    /**
     * Bỏ qua thay vì làm hỏng build khi máy không chạy được Docker. CI có Docker nên ở đó test
     * vẫn chạy thật.
     */
    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired private NotificationRepository repository;
    @Autowired private MongoTemplate mongoTemplate;

    private MongoIndexInitializer initializer;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        initializer = new MongoIndexInitializer(mongoTemplate);
    }

    private static Notification notification(String id, String email, Instant readAt) {
        return Notification.builder()
                .id(id)
                .userEmail(email)
                .type(NotificationType.ACCOUNT_ACTIVATED)
                .title("Tài khoản đã được kích hoạt")
                .body("Chào mừng!")
                .readAt(readAt)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    @DisplayName("_id trùng bị MongoDB từ chối — đây là cơ chế chống trùng của kênh in-app")
    void id_trung_bi_tu_choi() {
        repository.insert(notification("evt-1:IN_APP", "alice@novaplay.vn", null));

        assertThatThrownBy(() -> repository.insert(notification("evt-1:IN_APP", "alice@novaplay.vn", null)))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("khởi tạo index chạy lần thứ hai không ăn lỗi 85 IndexOptionsConflict")
    void khoi_tao_index_idempotent() {
        initializer.run(noArgs());

        // Lần chạy thứ hai là kịch bản thật: mọi lần restart pod đều đi qua đây, và so khớp index
        // theo TÊN thay vì theo bộ khoá sẽ làm bước này ném lỗi và pod không khởi động được.
        assertThatCode(() -> initializer.run(noArgs())).doesNotThrowAnyException();
    }

    @Test
    void tao_du_ba_index_can_thiet() {
        initializer.run(noArgs());

        List<String> names = mongoTemplate.indexOps(Notification.class).getIndexInfo()
                .stream().map(IndexInfo::getName).toList();
        assertThat(names).contains(IDX_USER_CREATED, IDX_USER_UNREAD, IDX_EXPIRES_TTL);
    }

    @Test
    @DisplayName("TTL index được tạo với expireAfterSeconds=0 để dọn theo mốc expiresAt")
    void ttl_index_dung_cau_hinh() {
        initializer.run(noArgs());

        IndexInfo ttl = mongoTemplate.indexOps(Notification.class).getIndexInfo().stream()
                .filter(i -> IDX_EXPIRES_TTL.equals(i.getName()))
                .findFirst().orElseThrow();
        assertThat(ttl.getExpireAfter()).isPresent();
        assertThat(ttl.getExpireAfter().get().toSeconds()).isZero();
    }

    @Test
    void truy_van_chua_doc_chi_tra_ve_cua_dung_nguoi() {
        repository.insert(notification("a:IN_APP", "alice@novaplay.vn", null));
        repository.insert(notification("b:IN_APP", "alice@novaplay.vn", Instant.now()));
        repository.insert(notification("c:IN_APP", "bob@novaplay.vn", null));

        assertThat(repository.countByUserEmailAndReadAtIsNull("alice@novaplay.vn")).isEqualTo(1);
        assertThat(repository.countByUserEmailAndReadAtIsNull("bob@novaplay.vn")).isEqualTo(1);

        var page = repository.findByUserEmail("alice@novaplay.vn",
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, CREATED_AT)));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(n -> n.getUserEmail().equals("alice@novaplay.vn"));
    }

    @Test
    @DisplayName("@CreatedDate được điền — thiếu @Version thì Spring Data coi document đã tồn tại và bỏ qua auditing")
    void created_at_duoc_dien() {
        repository.insert(notification("evt-1:IN_APP", "alice@novaplay.vn", null));

        Notification saved = repository.findById("evt-1:IN_APP").orElseThrow();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    private static ApplicationArguments noArgs() {
        return new org.springframework.boot.DefaultApplicationArguments();
    }
}
