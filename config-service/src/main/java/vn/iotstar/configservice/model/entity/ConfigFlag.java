package vn.iotstar.configservice.model.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Một cặp key/value cấu hình động (feature flag, tham số vận hành, ...) mà các service khác đọc
 * qua {@code ConfigFlagController}. {@code key} chính là {@code _id} — tra cứu O(1) theo khoá
 * chính, không cần index phụ. Không có endpoint ghi ở v1: sửa giá trị bằng cách sửa document này
 * trực tiếp (mongo-express hoặc mongosh) — xem README/plan cho lý do.
 */
@Document(collection = "config_flags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfigFlag {

    @Id
    private String key;

    private String value;
}
