package vn.iotstar.mediaservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import vn.iotstar.mediaservice.util.AuditableDocument;
import vn.iotstar.mediaservice.util.Constants;
import vn.iotstar.mediaservice.util.MediaStatus;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Document(collection = Constants.MEDIA_COLLECTION)
@CompoundIndexes({
        @CompoundIndex(name = Constants.IDX_MEDIA_OWNER_ID, def = "{'" + Constants.MEDIA_OWNER_ID + "': 1}"),
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Media extends AuditableDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Field(Constants.MEDIA_ID)
    private String id;

    @Field(Constants.MEDIA_OWNER_ID)
    @Indexed(unique = true)
    private String ownerId;

    @Field(Constants.MEDIA_ORIGINAL_FILENAME)
    private String originalFileName;

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
}
