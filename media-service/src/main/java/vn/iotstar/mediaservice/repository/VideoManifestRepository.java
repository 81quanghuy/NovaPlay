package vn.iotstar.mediaservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.mediaservice.entity.VideoManifest;
import vn.iotstar.mediaservice.util.VideoManifestStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoManifestRepository extends MongoRepository<VideoManifest, String> {

    Optional<VideoManifest> findBySourceMediaId(String sourceMediaId);

    /** Job đối soát: manifest kẹt ở PROCESSING quá lâu vì pod worker chết giữa chừng. */
    List<VideoManifest> findByStatusAndProcessingStartedAtBefore(VideoManifestStatus status, Instant threshold);
}
