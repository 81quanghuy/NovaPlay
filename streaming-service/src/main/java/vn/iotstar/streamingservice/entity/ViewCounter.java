package vn.iotstar.streamingservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import vn.iotstar.streamingservice.utils.Constants;

import java.io.Serial;
import java.io.Serializable;

/** {@code _id} = movieId. Tăng qua {@code $inc} nguyên tử (xem ViewCountServiceImpl), không đọc-sửa-ghi. */
@Document(collection = Constants.VIEW_COUNT_COLLECTION)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ViewCounter implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String movieId;

    private long totalViews;
}
