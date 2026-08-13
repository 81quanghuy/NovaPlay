package vn.iotstar.mediaservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.mediaservice.storage.StorageProvider;
import vn.iotstar.mediaservice.util.Constants;
import vn.iotstar.mediaservice.util.MediaStatus;
import vn.iotstar.mediaservice.common.audit.AuditableDocument;

import java.io.Serial;
import java.io.Serializable;

@Document(collection = Constants.MEDIA_COLLECTION)
@CompoundIndex(name = Constants.IDX_MEDIA_OWNER_ID, def = "{'" + Constants.MEDIA_OWNER_ID + "': 1}")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Media extends AuditableDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Field(Constants.MEDIA_ID)
    private String id;

    @Field(Constants.MEDIA_OWNER_ID)
    private String ownerId;

    /**
     * Chủ sở hữu thực sự theo danh tính xác thực (email từ header {@code X-User-Email} do gateway
     * bơm vào). Luôn do server tự set từ {@code SecurityContextHolder} lúc tạo record — KHÔNG bao
     * giờ nhận trực tiếp từ input client, để tránh giả mạo chủ sở hữu (IDOR).
     */
    @Field(Constants.MEDIA_OWNER_EMAIL)
    private String ownerEmail;

    @Field(Constants.MEDIA_ORIGINAL_FILENAME)
    private String originalFileName;

    /**
     * Namespaced theo mediaId ({@code media/{ownerId}/{mediaId}/{fileName}}) nên là bất biến: một
     * khi đã sinh, key này không bao giờ bị ghi đè bởi một lần upload khác — an toàn để cache CDN
     * dài hạn (ví dụ {@code Cache-Control: max-age=31536000, immutable}; header đó không cần set ở
     * đây vì response của bước upload không đi qua CDN).
     */
    @Field(Constants.MEDIA_S3_KEY)
    @Indexed(unique = true)
    private String s3Key;

    @Field(Constants.MEDIA_CDN_URL)
    private String cdnUrl;

    @Field(Constants.CONTENT_TYPE)
    private String contentType;

    @Field(Constants.SIZE)
    private Long size;

    @Builder.Default
    @Field(Constants.MEDIA_STATUS)
    private MediaStatus status = MediaStatus.PENDING;

    /**
     * Chỉ set khi upload đi qua đường multipart (video lớn) — bắt buộc cho video vì presigned PUT
     * đơn không dùng được với file GB (mất kết nối là mất sạch, không resume). Null với ảnh (vẫn
     * dùng single-PUT presigned URL như trước).
     */
    @Field("multipartUploadId")
    private String multipartUploadId;

    /**
     * Provider đã dùng lúc upload — ghi lại tại thời điểm tạo record, KHÔNG BAO GIỜ suy ra lại từ
     * cờ sống sau đó. Xem {@link #getEffectiveStorageProvider()}.
     */
    @Field(Constants.MEDIA_STORAGE_PROVIDER)
    private StorageProvider storageProvider;

    /**
     * Field vắng mặt (record tạo trước khi tính năng multi-provider tồn tại) nghĩa là
     * {@link StorageProvider#AWS_S3} — 100% record hiện có trong prod thực sự đang nằm trên AWS S3,
     * đây là sự thật lịch sử chứ không phải suy đoán. Dùng getter này ở MỌI nơi thao tác trên một
     * {@link Media} ĐÃ TỒN TẠI (xoá, kiểm tra tồn tại, sinh CDN URL) — không bao giờ hỏi lại cờ sống
     * (xem {@link vn.iotstar.mediaservice.storage.StorageProviderResolver}) cho record cũ, vì cờ có
     * thể đã đổi sang provider khác từ lúc record này được tạo.
     */
    public StorageProvider getEffectiveStorageProvider() {
        return storageProvider != null ? storageProvider : StorageProvider.AWS_S3;
    }
}
