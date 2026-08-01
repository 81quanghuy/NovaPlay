package vn.iotstar.mediaservice.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.repository.MediaRepository;
import vn.iotstar.mediaservice.util.MediaStatus;
import vn.iotstar.utils.dto.MediaReadyEvent;
import vn.iotstar.utils.dto.UploadRequestDto;
import vn.iotstar.utils.dto.UploadResponseDto;
import vn.iotstar.utils.exceptions.wrapper.BadRequestException;
import vn.iotstar.utils.exceptions.wrapper.ForbiddenException;
import vn.iotstar.utils.exceptions.wrapper.ResourceNotFoundException;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaServiceImplTest {

    private static final String BUCKET = "novaplay-media-test";
    private static final String CDN_BASE_URL = "https://cdn.novaplay.vn";

    @Mock private S3Presigner s3Presigner;
    @Mock private MediaRepository mediaRepository;
    @Mock private org.springframework.kafka.core.KafkaTemplate<String, MediaReadyEvent> kafkaTemplate;
    @Mock private S3Client s3Client;

    @InjectMocks
    private MediaServiceImpl service;

    @BeforeEach
    void setUp() throws MalformedURLException {
        ReflectionTestUtils.setField(service, "durationMinutes", 15L);
        ReflectionTestUtils.setField(service, "bucketName", BUCKET);
        ReflectionTestUtils.setField(service, "cdnBaseUrl", CDN_BASE_URL);

        when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> inv.getArgument(0));

        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3.local/" + BUCKET + "/signed").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private UploadRequestDto validRequest() {
        return new UploadRequestDto("owner-1", "photo.jpg", "image/jpeg", 1024L);
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    // ---------- S3 key namespacing (bug fix #1) ----------

    @Nested
    @DisplayName("sinh S3 key")
    class KeyGeneration {

        @Test
        @DisplayName("key namespaced theo mediaId, không còn ownerId/fileName trần")
        void generatesS3KeyNamespacedByMediaId() {
            UploadResponseDto response = service.requestUploadUrl(validRequest());

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            Media saved = captor.getValue();

            assertThat(saved.getId()).isEqualTo(response.mediaId());
            assertThat(saved.getS3Key())
                    .isEqualTo("media/owner-1/" + saved.getId() + "/photo.jpg");
        }

        @Test
        @DisplayName("hai request trùng tên file sinh ra hai key khác nhau (không còn ghi đè)")
        void twoUploadsWithSameFileNameGetDistinctKeys() {
            UploadResponseDto first = service.requestUploadUrl(validRequest());
            UploadResponseDto second = service.requestUploadUrl(validRequest());

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository, times(2)).save(captor.capture());

            List<Media> saved = captor.getAllValues();
            assertThat(first.mediaId()).isNotEqualTo(second.mediaId());
            assertThat(saved.get(0).getS3Key()).isNotEqualTo(saved.get(1).getS3Key());
        }
    }

    // ---------- Presigned URL content-type/length signing (bug fix #3) ----------

    @Nested
    @DisplayName("ký presigned PUT")
    class PresignedUrlSigning {

        @Test
        @DisplayName("Content-Type và Content-Length được ký vào PutObjectRequest")
        void signsContentTypeAndContentLength() {
            service.requestUploadUrl(validRequest());

            ArgumentCaptor<PutObjectPresignRequest> captor =
                    ArgumentCaptor.forClass(PutObjectPresignRequest.class);
            verify(s3Presigner).presignPutObject(captor.capture());

            PutObjectRequest putObjectRequest = captor.getValue().putObjectRequest();
            assertThat(putObjectRequest.bucket()).isEqualTo(BUCKET);
            assertThat(putObjectRequest.contentType()).isEqualTo("image/jpeg");
            assertThat(putObjectRequest.contentLength()).isEqualTo(1024L);
        }
    }

    // ---------- Upload validation (bug fix #2) ----------

    @Nested
    @DisplayName("validate trước khi cấp presigned URL")
    class UploadValidation {

        @Test
        @DisplayName("content-type ngoài allowlist bị từ chối bằng BadRequestException")
        void rejectsDisallowedContentType() {
            UploadRequestDto request = new UploadRequestDto("owner-1", "doc.pdf", "application/pdf", 1024L);

            assertThatThrownBy(() -> service.requestUploadUrl(request))
                    .isInstanceOf(BadRequestException.class);

            verifyNoInteractions(mediaRepository, s3Presigner);
        }

        @Test
        @DisplayName("file vượt quá kích thước tối đa bị từ chối")
        void rejectsOversizedFile() {
            long tooBig = MediaServiceImpl.MAX_UPLOAD_SIZE_BYTES + 1;
            UploadRequestDto request = new UploadRequestDto("owner-1", "big.jpg", "image/jpeg", tooBig);

            assertThatThrownBy(() -> service.requestUploadUrl(request))
                    .isInstanceOf(BadRequestException.class);

            verifyNoInteractions(mediaRepository, s3Presigner);
        }

        @Test
        @DisplayName("kích thước không dương bị từ chối")
        void rejectsNonPositiveSize() {
            UploadRequestDto request = new UploadRequestDto("owner-1", "empty.jpg", "image/jpeg", 0L);

            assertThatThrownBy(() -> service.requestUploadUrl(request))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("thiếu content-type bị từ chối")
        void rejectsMissingContentType() {
            UploadRequestDto request = new UploadRequestDto("owner-1", "file", null, 1024L);

            assertThatThrownBy(() -> service.requestUploadUrl(request))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ---------- ownerEmail set from SecurityContext (fix #5) ----------

    @Test
    @DisplayName("ownerEmail được set từ SecurityContext, không phải từ input client")
    void setsOwnerEmailFromSecurityContext() {
        authenticateAs("caller@novaplay.vn");

        service.requestUploadUrl(validRequest());

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerEmail()).isEqualTo("caller@novaplay.vn");
    }

    // ---------- Ownership checks: GET /{id} ----------

    @Nested
    @DisplayName("getMediaById — ownership check")
    class GetMediaByIdOwnership {

        private Media mediaOwnedBy(String email) {
            Media media = new Media();
            media.setId("m1");
            media.setOwnerEmail(email);
            media.setS3Key("media/owner-1/m1/photo.jpg");
            media.setStatus(MediaStatus.COMPLETED);
            return media;
        }

        @Test
        @DisplayName("chủ sở hữu xem được media của mình")
        void ownerCanAccessOwnMedia() {
            when(mediaRepository.findById("m1")).thenReturn(Optional.of(mediaOwnedBy("a@x.com")));

            Media result = service.getMediaById("m1", "a@x.com", false);

            assertThat(result.getId()).isEqualTo("m1");
        }

        @Test
        @DisplayName("người khác không phải admin bị chặn 403 (IDOR)")
        void nonOwnerNonAdminIsForbidden() {
            when(mediaRepository.findById("m1")).thenReturn(Optional.of(mediaOwnedBy("a@x.com")));

            assertThatThrownBy(() -> service.getMediaById("m1", "b@x.com", false))
                    .isInstanceOf(ForbiddenException.class);
        }

        @Test
        @DisplayName("admin xem được media của người khác")
        void adminCanAccessAnyMedia() {
            when(mediaRepository.findById("m1")).thenReturn(Optional.of(mediaOwnedBy("a@x.com")));

            Media result = service.getMediaById("m1", "b@x.com", true);

            assertThat(result.getId()).isEqualTo("m1");
        }

        @Test
        @DisplayName("id không tồn tại ném ResourceNotFoundException")
        void missingMediaThrowsNotFound() {
            when(mediaRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMediaById("missing", "a@x.com", false))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ---------- Delete: soft-delete record + hard-delete S3 object ----------

    @Nested
    @DisplayName("deleteMedia")
    class DeleteMedia {

        private Media pendingMedia() {
            Media media = new Media();
            media.setId("m1");
            media.setOwnerEmail("a@x.com");
            media.setS3Key("media/owner-1/m1/photo.jpg");
            media.setStatus(MediaStatus.COMPLETED);
            return media;
        }

        @Test
        @DisplayName("chủ sở hữu xoá được: soft-delete record + hard-delete S3 object")
        void ownerCanDelete() {
            Media media = pendingMedia();
            when(mediaRepository.findById("m1")).thenReturn(Optional.of(media));

            service.deleteMedia("m1", "a@x.com", false);

            ArgumentCaptor<DeleteObjectRequest> s3Captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(s3Captor.capture());
            assertThat(s3Captor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(s3Captor.getValue().key()).isEqualTo("media/owner-1/m1/photo.jpg");

            ArgumentCaptor<Media> savedCaptor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(savedCaptor.capture());
            assertThat(savedCaptor.getValue().getStatus()).isEqualTo(MediaStatus.DELETED);
        }

        @Test
        @DisplayName("admin xoá được media của người khác")
        void adminCanDeleteAnyMedia() {
            when(mediaRepository.findById("m1")).thenReturn(Optional.of(pendingMedia()));

            service.deleteMedia("m1", "someone-else@x.com", true);

            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
            verify(mediaRepository).save(any(Media.class));
        }

        @Test
        @DisplayName("người khác không phải admin bị chặn 403, không có gì bị xoá")
        void nonOwnerNonAdminIsForbiddenAndNothingDeleted() {
            when(mediaRepository.findById("m1")).thenReturn(Optional.of(pendingMedia()));

            assertThatThrownBy(() -> service.deleteMedia("m1", "b@x.com", false))
                    .isInstanceOf(ForbiddenException.class);

            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
            verify(mediaRepository, never()).save(any(Media.class));
        }

        @Test
        @DisplayName("id không tồn tại ném ResourceNotFoundException")
        void missingMediaThrowsNotFound() {
            when(mediaRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteMedia("missing", "a@x.com", false))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ---------- Cleanup job: orphaned S3 object deleted before marking FAILED (fix #4) ----------

    @Nested
    @DisplayName("markAsFailed — dọn object mồ côi")
    class MarkAsFailed {

        @Test
        @DisplayName("xoá object trên S3 trước khi lưu trạng thái FAILED")
        void deletesS3ObjectBeforeSavingFailedStatus() {
            Media media = new Media();
            media.setId("m1");
            media.setS3Key("media/owner-1/m1/orphan.jpg");
            media.setStatus(MediaStatus.PENDING);

            service.markAsFailed(media);

            var inOrder = inOrder(s3Client, mediaRepository);
            inOrder.verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
            inOrder.verify(mediaRepository).save(media);

            ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertThat(captor.getValue().key()).isEqualTo("media/owner-1/m1/orphan.jpg");
            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(media.getStatus()).isEqualTo(MediaStatus.FAILED);
        }

        @Test
        @DisplayName("lỗi xoá S3 không chặn việc đánh dấu FAILED (best-effort)")
        void continuesMarkingFailedEvenIfS3DeleteFails() {
            Media media = new Media();
            media.setId("m1");
            media.setS3Key("media/owner-1/m1/orphan.jpg");
            media.setStatus(MediaStatus.PENDING);

            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(S3Exception.builder().message("boom").build());

            service.markAsFailed(media);

            assertThat(media.getStatus()).isEqualTo(MediaStatus.FAILED);
            verify(mediaRepository).save(media);
        }
    }

    // ---------- Repository delegation for /me and admin-list ----------

    @Test
    @DisplayName("getMyMedia delegates to findByOwnerEmail")
    void getMyMediaDelegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Media> page = Page.empty();
        when(mediaRepository.findByOwnerEmail("a@x.com", pageable)).thenReturn(page);

        Page<Media> result = service.getMyMedia("a@x.com", pageable);

        assertThat(result).isSameAs(page);
        verify(mediaRepository).findByOwnerEmail("a@x.com", pageable);
    }

    @Test
    @DisplayName("getMediaByOwnerId delegates to findByOwnerId (endpoint admin-list)")
    void getMediaByOwnerIdDelegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Media> page = Page.empty();
        when(mediaRepository.findByOwnerId("owner-1", pageable)).thenReturn(page);

        Page<Media> result = service.getMediaByOwnerId("owner-1", pageable);

        assertThat(result).isSameAs(page);
        verify(mediaRepository).findByOwnerId("owner-1", pageable);
    }
}
