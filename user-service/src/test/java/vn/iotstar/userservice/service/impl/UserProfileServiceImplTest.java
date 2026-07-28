package vn.iotstar.userservice.service.impl;

import io.micrometer.core.instrument.Counter;
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
import org.springframework.dao.DuplicateKeyException;
import vn.iotstar.userservice.config.observability.UserMetrics;
import vn.iotstar.userservice.model.dto.UpdateUserProfileRequest;
import vn.iotstar.userservice.model.dto.UserProfileDTO;
import vn.iotstar.userservice.model.entity.UserProfile;
import vn.iotstar.userservice.repository.IUserProfileRepository;
import vn.iotstar.userservice.service.AuditLogger;
import vn.iotstar.userservice.service.UserProfileLookup;
import vn.iotstar.userservice.service.client.MediaServiceClient;
import vn.iotstar.userservice.util.Plan;
import vn.iotstar.utils.dto.UserRegister;
import vn.iotstar.utils.exceptions.wrapper.ResourceNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileServiceImplTest {

    private static final String EMAIL = "user@example.com";

    @Mock private MediaServiceClient mediaServiceClient;
    @Mock private IUserProfileRepository userProfileRepository;
    @Mock private UserProfileLookup userProfileLookup;
    @Mock private UserMetrics metrics;
    @Mock private AuditLogger auditLogger;
    @Mock private Counter counter;

    @InjectMocks private UserProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        when(metrics.getProfileUpdated()).thenReturn(counter);
        when(metrics.getAvatarUploadRequested()).thenReturn(counter);
    }

    private static UserProfile profile() {
        UserProfile p = UserProfile.builder()
                .id("user-1")
                .email(EMAIL)
                .displayName("Original Name")
                .plan(Plan.MEMBER)
                .build();
        p.setActive(true);
        return p;
    }

    @Nested
    @DisplayName("registerNewUser")
    class RegisterNewUser {

        @Test
        @DisplayName("tạo profile mới khi email chưa tồn tại")
        void createsProfileWhenEmailIsNew() {
            when(userProfileRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            service.registerNewUser(new UserRegister("newuser", EMAIL));

            ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
            verify(userProfileRepository).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo(EMAIL);
            assertThat(captor.getValue().getDisplayName()).isEqualTo("newuser");
            assertThat(captor.getValue().getPlan()).isEqualTo(Plan.MEMBER);
            assertThat(captor.getValue().getId()).isNotBlank();
        }

        @Test
        @DisplayName("không tạo bản ghi thứ hai khi event bị giao lại")
        void isIdempotentOnRedelivery() {
            when(userProfileRepository.findByEmail(EMAIL)).thenReturn(Optional.of(profile()));

            service.registerNewUser(new UserRegister("newuser", EMAIL));

            verify(userProfileRepository, never()).save(any());
        }

        @Test
        @DisplayName("nuốt DuplicateKeyException khi hai consumer chạy song song")
        void swallowsDuplicateKeyFromConcurrentConsumers() {
            when(userProfileRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(userProfileRepository.save(any())).thenThrow(new DuplicateKeyException("dup"));

            // Không được ném ra ngoài: bản ghi đã tồn tại nghĩa là mục tiêu đã đạt được.
            service.registerNewUser(new UserRegister("newuser", EMAIL));

            verify(auditLogger, never()).profileRegistered(any());
        }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfile {

        @Test
        @DisplayName("áp dụng đủ các field có thể cập nhật")
        void appliesAllUpdatableFields() {
            UserProfile existing = profile();
            when(userProfileLookup.findByEmailOrThrow(EMAIL)).thenReturn(existing);
            when(userProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                    "newhandle", "New Name", "https://cdn/x.png", "vi-VN", Plan.PLUS, true);

            UserProfileDTO result = service.updateProfile(EMAIL, request);

            assertThat(existing.getPreferredUsername()).isEqualTo("newhandle");
            assertThat(existing.getDisplayName()).isEqualTo("New Name");
            // avatarUrl và plan trước đây bị bỏ qua hoàn toàn.
            assertThat(existing.getAvatarUrl()).isEqualTo("https://cdn/x.png");
            assertThat(existing.getPlan()).isEqualTo(Plan.PLUS);
            assertThat(existing.getLocale()).isEqualTo("vi-VN");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("giữ nguyên field khi request để null")
        void leavesNullFieldsUntouched() {
            UserProfile existing = profile();
            when(userProfileLookup.findByEmailOrThrow(EMAIL)).thenReturn(existing);
            when(userProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.updateProfile(EMAIL,
                    new UpdateUserProfileRequest(null, null, null, null, null, null));

            assertThat(existing.getDisplayName()).isEqualTo("Original Name");
            assertThat(existing.getPlan()).isEqualTo(Plan.MEMBER);
        }

        @Test
        @DisplayName("ghi marketingOptInAt lần đầu bật, không ghi đè lần sau")
        void stampsMarketingOptInOnlyOnTransition() {
            UserProfile existing = profile();
            when(userProfileLookup.findByEmailOrThrow(EMAIL)).thenReturn(existing);
            when(userProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            service.updateProfile(EMAIL,
                    new UpdateUserProfileRequest(null, null, null, null, null, true));
            var firstStamp = existing.getMarketingOptInAt();
            assertThat(firstStamp).isNotNull();

            service.updateProfile(EMAIL,
                    new UpdateUserProfileRequest(null, null, null, null, null, true));
            assertThat(existing.getMarketingOptInAt()).isEqualTo(firstStamp);
        }
    }

    @Nested
    @DisplayName("changeUrlAvatar")
    class ChangeUrlAvatar {

        @Test
        @DisplayName("ném ResourceNotFoundException khi userId không tồn tại")
        void throwsWhenUserMissing() {
            when(userProfileRepository.findById("ghost")).thenReturn(Optional.empty());

            // Phải là exception domain, không phải ResponseStatusException: đường chạy này
            // nằm trong consumer Kafka, nơi ngữ nghĩa HTTP vô nghĩa.
            assertThatThrownBy(() -> service.changeUrlAvatar("https://cdn/a.png", "ghost"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("ghost");
        }

        @Test
        @DisplayName("cập nhật avatar khi tìm thấy user")
        void updatesAvatarUrl() {
            UserProfile existing = profile();
            when(userProfileRepository.findById("user-1")).thenReturn(Optional.of(existing));

            service.changeUrlAvatar("https://cdn/new.png", "user-1");

            assertThat(existing.getAvatarUrl()).isEqualTo("https://cdn/new.png");
            verify(userProfileRepository).save(existing);
        }
    }
}
