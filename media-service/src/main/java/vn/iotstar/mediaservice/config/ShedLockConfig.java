package vn.iotstar.mediaservice.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Khoá phân tán cho các job định kỳ của media-service.
 * <p>
 * Cần thiết vì service chạy nhiều pod (replicas=2, HPA tới 6) và job trong
 * {@link PendingMediaCleanupJob} có tác dụng phụ TỐN KÉM chứ không chỉ đọc: nó phát
 * {@code video-source-ready.v1} và re-queue manifest kẹt. Không có khoá thì mỗi lượt chạy nhân
 * lên theo số pod, và mỗi bản trùng là một pod ffmpeg 2 vCPU/4Gi chạy lại đúng video đó — trên
 * cụm 1–2 VPS thì đủ để làm nghẽn mọi thứ còn lại.
 * <p>
 * Dùng provider Mongo vì service này đã có Mongo; ShedLock tự tạo collection {@code shedLock}.
 * Không kéo thêm datastore nào chỉ để giữ khoá.
 */
@Configuration
// defaultLockAtMostFor là lưới an toàn cho trường hợp pod giữ khoá bị kill đột ngột (OOM, node
// mất) và không kịp nhả: hết thời gian này khoá tự hết hiệu lực. Đặt dài hơn hẳn thời gian chạy
// tệ nhất của job, nhưng ngắn hơn chu kỳ 1 giờ để một lần chết không làm mất trọn hai chu kỳ.
@EnableSchedulerLock(defaultLockAtMostFor = "PT20M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(MongoTemplate mongoTemplate) {
        return new MongoLockProvider(mongoTemplate.getDb());
    }
}
