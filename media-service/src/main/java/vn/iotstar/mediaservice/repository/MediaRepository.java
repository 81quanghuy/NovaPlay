package vn.iotstar.mediaservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.util.MediaStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MediaRepository extends MongoRepository<Media, String> {

    Optional<Media> findByS3Key(String s3Key);

    List<Media> findByStatusAndCreatedAtBefore(MediaStatus status, Instant createdAt);

    /** Media của caller, dùng cho {@code GET /api/v1/media/me}. */
    Page<Media> findByOwnerEmail(String ownerEmail, Pageable pageable);

    /** Media của một owner cụ thể, dùng cho endpoint admin-list ({@code ROLE_ADMIN} only). */
    Page<Media> findByOwnerId(String ownerId, Pageable pageable);
}
