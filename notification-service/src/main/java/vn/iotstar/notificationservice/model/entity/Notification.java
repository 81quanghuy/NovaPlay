package vn.iotstar.notificationservice.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.notificationservice.model.enums.NotificationType;
import vn.iotstar.notificationservice.util.Constants;
import vn.iotstar.utils.audit.AuditableDocument;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = Constants.NOTIFICATION_COLLECTION)
@TypeAlias("Notification")
public class Notification extends AuditableDocument {

    /**
     * Cố tình gán tường minh thay vì để MongoDB tự sinh: {@code <dedupKey>:IN_APP} chính là cơ
     * chế chống trùng của kênh này — chèn lại cùng một sự kiện sẽ vi phạm unique index có sẵn
     * trên {@code _id} và ném {@link org.springframework.dao.DuplicateKeyException}, được
     * {@code InAppChannel} coi là "đã gửi rồi", không phải lỗi.
     */
    @Id
    private String id;

    /**
     * Bắt buộc phải có dù không dùng optimistic locking cho document này.
     * <p>
     * Id được gán sẵn phía ứng dụng trước khi insert, nên nếu thiếu {@code @Version}, Spring Data
     * coi document là đã tồn tại và bỏ qua auditing {@code @CreatedDate}/{@code @CreatedBy} —
     * đúng lỗi mà {@code Movie} của movie-service từng dính.
     */
    @Version
    private Long version;

    @Field("message_id")
    private String messageId;

    /** Có thể null: sự kiện activate-account.v1 chỉ mang email, không mang userId. */
    @Field("user_id")
    private String userId;

    @Field(Constants.USER_EMAIL)
    private String userEmail;

    @Field("type")
    private NotificationType type;

    /** Đã render sẵn theo locale tại thời điểm tiêu thụ sự kiện — đọc lại không cần render lại. */
    @Field("title")
    private String title;

    @Field("body")
    private String body;

    /** Dữ liệu phụ để FE điều hướng: deep link, id đối tượng liên quan... */
    @Builder.Default
    @Field("data")
    private Map<String, String> data = new LinkedHashMap<>();

    /** {@code null} nghĩa là chưa đọc. */
    @Field(Constants.READ_AT)
    private Instant readAt;

    /** Mốc cho TTL index — tự dọn thông báo cũ, không cần job xoá riêng. */
    @Field(Constants.EXPIRES_AT)
    private Instant expiresAt;
}
