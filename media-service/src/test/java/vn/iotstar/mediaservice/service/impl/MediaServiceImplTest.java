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
import software.amazon.awssdk.services.s3.model.S3Exception;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.repository.MediaRepository;
import vn.iotstar.mediaservice.service.MediaStorageService;
import vn.iotstar.mediaservice.storage.StorageProvider;
import vn.iotstar.mediaservice.storage.StorageProviderResolver;
import vn.iotstar.mediaservice.util.MediaStatus;
import vn.iotstar.mediaservice.common.dto.MediaReadyEvent;
import vn.iotstar.mediaservice.common.dto.UploadRequestDto;
import vn.iotstar.mediaservice.common.dto.UploadResponseDto;
import vn.iotstar.mediaservice.exception.BadRequestException;
import vn.iotstar.mediaservice.exception.ForbiddenException;
import vn.iotstar.mediaservice.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaServiceImplTest {

    private static final String PRESIGNED_URL = "https://storage.local/signed";

    @Mock private MediaStorageService mediaStorageService;
    @Mock private StorageProviderResolver storageProviderResolver;
    @Mock private MediaRepository mediaRepository;
    @Mock private org.springframework.kafka.core.KafkaTemplate<String, MediaReadyEvent> kafkaTemplate;

    @InjectMocks
    private MediaServiceImpl service;

    @BeforeEach
    void setUp() {
        when(mediaRepository.save(any(Media.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storageProviderResolver.resolveForNewUpload()).thenReturn(StorageProvider.AWS_S3);
        when(mediaStorageService.generatePresignedUploadUrl(any(), any(), any(), anyLong()))
                .thenReturn(PRESIGNED_URL);
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

    // ---------- Storage provider toggle: chỉ upload MỚI mới hỏi cờ sống ----------

    @Nested
    @DisplayName("chọn storage provider")
    class StorageProviderSelection {

        @Test
        @DisplayName("requestUploadUrl dùng provider mà cờ sống trả về, và lưu lại trên Media")
        void requestUploadUrlPersistsFlagResolvedProvider() {
            when(storageProviderResolver.resolveForNewUpload()).thenReturn(StorageProvider.CLOUDFLARE_R2);

            service.requestUploadUrl(validRequest());

            verify(mediaStorageService).generatePresignedUploadUrl(
                    eq(StorageProvider.CLOUDFLARE_R2), any(), eq("image/jpeg"), eq(1024L));

            ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(captor.capture());
            assertThat(captor.getValue().getStorageProvider()).isEqualTo(StorageProvider.CLOUDFLARE_R2);
        }

        @Test
        @DisplayName("deleteMedia dùng provider đã lưu trên record, KHÔNG hỏi lại cờ sống")
        void deleteMediaUsesPersistedProviderNotLiveFlag() {
            Media media = new Media();
            media.setId("m1");
            media.setOwnerEmail("a@x.com");
            media.setS3Key("media/owner-1/m1/photo.jpg");
            media.setStatus(MediaStatus.COMPLETED);
            media.setStorageProvider(StorageProvider.AWS_S3);
            when(mediaRepository.findById("m1")).thenReturn(Optional.of(media));

            service.deleteMedia("m1", "a@x.com", false);

            verify(mediaStorageService).deleteObject(StorageProvider.AWS_S3, "media/owner-1/m1/photo.jpg");
            verifyNoInteractions(storageProviderResolver);
        }

        @Test
        @DisplayName("storageProvider null (record cũ trước khi tính năng multi-provider tồn tại) mặc định AWS_S3")
        void nullStorageProviderDefaultsToAwsS3() {
            Media media = new Media();
            media.setId("m1");
            media.setOwnerEmail("a@x.com");
            media.setS3Key("media/owner-1/m1/photo.jpg");
            media.setStatus(MediaStatus.COMPLETED);
            // storageProvider cố tình để null.
            when(mediaRepository.findById("m1")).thenReturn(Optional.of(media));

            service.deleteMedia("m1", "a@x.com", false);

            verify(mediaStorageService).deleteObject(StorageProvider.AWS_S3, "media/owner-1/m1/photo.jpg");
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

            verifyNoInteractions(mediaRepository, mediaStorageService);
        }

        @Test
        @DisplayName("file vượt quá kích thước tối đa bị từ chối")
        void rejectsOversizedFile() {
            long tooBig = MediaServiceImpl.MAX_UPLOAD_SIZE_BYTES + 1;
            UploadRequestDto request = new UploadRequestDto("owner-1", "big.jpg", "image/jpeg", tooBig);

            assertThatThrownBy(() -> service.requestUploadUrl(request))
                    .isInstanceOf(BadRequestException.class);

            verifyNoInteractions(mediaRepository, mediaStorageService);
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

    // ---------- Delete: soft-delete record + hard-delete object trên storage ----------

    @Nested
    @DisplayName("deleteMedia")
    class DeleteMedia {

        private Media pendingMedia() {
            Media media = new Media();
            media.setId("m1");
            media.setOwnerEmail("a@x.com");
            media.setS3Key("media/owner-1/m1/photo.jpg");
            media.setStatus(MediaStatus.COMPLETED);
            media.setStorageProvider(StorageProvider.AWS_S3);
            return media;
        }

        @Test
        @DisplayName("chủ sở hữu xoá được: soft-delete record + hard-delete object trên storage")
        void ownerCanDelete() {
            Media media = pendingMedia();
            when(mediaRepository.findById("m1")).thenReturn(Optional.of(media));

            service.deleteMedia("m1", "a@x.com", false);

            verify(mediaStorageService).deleteObject(StorageProvider.AWS_S3, "media/owner-1/m1/photo.jpg");

            ArgumentCaptor<Media> savedCaptor = ArgumentCaptor.forClass(Media.class);
            verify(mediaRepository).save(savedCaptor.capture());
            assertThat(savedCaptor.getValue().getStatus()).isEqualTo(MediaStatus.DELETED);
        }

        @Test
        @DisplayName("admin xoá được media của người khác")
        void adminCanDeleteAnyMedia() {
            when(mediaRepository.findById("m1")).thenReturn(Optional.of(pendingMedia()));

            service.deleteMedia("m1", "someone-else@x.com", true);

            verify(mediaStorageService).deleteObject(any(), any());
            verify(mediaRepository).save(any(Media.class));
        }

        @Test
        @DisplayName("người khác không phải admin bị chặn 403, không có gì bị xoá")
        void nonOwnerNonAdminIsForbiddenAndNothingDeleted() {
            when(mediaRepository.findById("m1")).thenReturn(Optional.of(pendingMedia()));

            assertThatThrownBy(() -> service.deleteMedia("m1", "b@x.com", false))
                    .isInstanceOf(ForbiddenException.class);

            verify(mediaStorageService, never()).deleteObject(any(), any());
            verify(mediaRepository, never()).save(any(Media.class));
        }

        @Test
        @DisplayName("id không tồn tại ném ResourceNotFoundException")
        void missingMediaThrowsNotFound() {
            when(mediaRepository.findById("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteMedia("missing", "a@x.com", false))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("lỗi xoá KHÔNG bị nuốt: văng ra ngoài và chặn luôn soft-delete record")
        void doesNotSwallowStorageErrorsUnlikeMarkAsFailed() {
            // Bất đối xứng có chủ đích so với markAsFailed/deleteObjectQuietly: đây là thao tác
            // xoá do người dùng chủ động yêu cầu, không thể im lặng để lại record "đã xoá" trong
            // khi object thật vẫn còn trên storage — brief coi thao tác này là "không thể thu hồi",
            // nên nó phải thực sự xảy ra hoặc báo lỗi rõ ràng, không phải best-effort.
            Media media = pendingMedia();
            when(mediaRepository.findById("m1")).thenReturn(Optional.of(media));
            doThrow(S3Exception.builder().message("boom").build())
                    .when(mediaStorageService).deleteObject(any(), any());

            assertThatThrownBy(() -> service.deleteMedia("m1", "a@x.com", false))
                    .isInstanceOf(S3Exception.class);

            verify(mediaRepository, never()).save(any(Media.class));
            assertThat(media.getStatus()).isEqualTo(MediaStatus.COMPLETED);
        }
    }

    // ---------- Cleanup job: orphaned object bị xoá trước khi đánh dấu FAILED (fix #4) ----------

    @Nested
    @DisplayName("markAsFailed — dọn object mồ côi")
    class MarkAsFailed {

        @Test
        @DisplayName("xoá object trên storage (best-effort) trước khi lưu trạng thái FAILED")
        void deletesObjectBeforeSavingFailedStatus() {
            Media media = new Media();
            media.setId("m1");
            media.setS3Key("media/owner-1/m1/orphan.jpg");
            media.setStatus(MediaStatus.PENDING);
            media.setStorageProvider(StorageProvider.AWS_S3);

            service.markAsFailed(media);

            var inOrder = inOrder(mediaStorageService, mediaRepository);
            inOrder.verify(mediaStorageService).deleteObjectQuietly(StorageProvider.AWS_S3, "media/owner-1/m1/orphan.jpg");
            inOrder.verify(mediaRepository).save(media);
            assertThat(media.getStatus()).isEqualTo(MediaStatus.FAILED);
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
