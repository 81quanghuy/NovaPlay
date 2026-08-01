package vn.iotstar.mediaservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.iotstar.mediaservice.config.security.FilterRegistrationConfig;
import vn.iotstar.mediaservice.config.security.GatewayAuthFilter;
import vn.iotstar.mediaservice.config.security.HeaderAuthenticationFilter;
import vn.iotstar.mediaservice.config.security.RestAuthenticationHandlers;
import vn.iotstar.mediaservice.config.security.SecurityConfig;
import vn.iotstar.mediaservice.entity.Media;
import vn.iotstar.mediaservice.service.MediaService;
import vn.iotstar.mediaservice.util.MediaStatus;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng ranh giới phân quyền của các endpoint mới trên {@code MediaController} — mirror
 * {@code MovieControllerSecurityTest}. Đặc biệt kiểm tra IDOR: {@code /me} chỉ trả media của
 * chính caller, {@code /{id}} và {@code DELETE /{id}} chặn người không phải chủ sở hữu/không phải
 * admin, và danh sách theo {@code ownerId} chỉ admin mới gọi được.
 */
@WebMvcTest(controllers = MediaController.class)
@Import({SecurityConfig.class, GatewayAuthFilter.class, HeaderAuthenticationFilter.class,
        FilterRegistrationConfig.class, RestAuthenticationHandlers.class,
        MediaControllerSecurityTest.TestBeans.class})
@TestPropertySource(properties = "application.security.gateway-secret.enabled=false")
class MediaControllerSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private MediaService mediaService;

    @TestConfiguration
    static class TestBeans {
        /**
         * Đăng ký {@link JavaTimeModule}: {@code GenericResponse.timestamp} (java.time.Instant)
         * được serialize khi {@code GlobalExceptionHandler} trả lỗi (ví dụ 403 từ
         * {@code ForbiddenException} ném ra bởi service mock) — thiếu module này Jackson ném
         * {@code InvalidDefinitionException} lúc ghi response body.
         */
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }

    private static final String ADMIN_EMAIL = "admin@novaplay.vn";
    private static final String OWNER_EMAIL = "owner@novaplay.vn";
    private static final String OTHER_EMAIL = "other@novaplay.vn";

    private Media media(String id, String ownerEmail) {
        Media media = new Media();
        media.setId(id);
        media.setOwnerEmail(ownerEmail);
        media.setOwnerId("owner-1");
        media.setS3Key("media/owner-1/" + id + "/photo.jpg");
        media.setStatus(MediaStatus.COMPLETED);
        return media;
    }

    // ---------- GET /api/v1/media/me ----------

    @Test
    @DisplayName("khách ẩn danh gọi /me bị chặn 401")
    void anonymousCannotAccessMe() throws Exception {
        mockMvc.perform(get("/api/v1/media/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("người dùng đã đăng nhập xem được media của chính mình qua /me")
    void authenticatedUserCanAccessMe() throws Exception {
        when(mediaService.getMyMedia(eq(OWNER_EMAIL), any()))
                .thenReturn(new PageImpl<>(List.of(media("m1", OWNER_EMAIL)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/media/me")
                        .header("X-User-Email", OWNER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]"))
                .andExpect(status().isOk());
    }

    // ---------- GET /api/v1/media/{id} ----------

    @Test
    @DisplayName("khách ẩn danh gọi GET /{id} bị chặn 401")
    void anonymousCannotAccessDetail() throws Exception {
        mockMvc.perform(get("/api/v1/media/m1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("chủ sở hữu xem được media của mình qua GET /{id}")
    void ownerCanAccessOwnDetail() throws Exception {
        when(mediaService.getMediaById(eq("m1"), eq(OWNER_EMAIL), eq(false)))
                .thenReturn(media("m1", OWNER_EMAIL));

        mockMvc.perform(get("/api/v1/media/m1")
                        .header("X-User-Email", OWNER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("admin xem được media của người khác qua GET /{id}")
    void adminCanAccessAnyDetail() throws Exception {
        when(mediaService.getMediaById(eq("m1"), eq(ADMIN_EMAIL), eq(true)))
                .thenReturn(media("m1", OWNER_EMAIL));

        mockMvc.perform(get("/api/v1/media/m1")
                        .header("X-User-Email", ADMIN_EMAIL)
                        .header("X-User-Roles", "[ROLE_ADMIN]"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("người dùng khác không phải chủ sở hữu, không phải admin bị chặn 403 (IDOR)")
    void nonOwnerNonAdminIsForbiddenOnDetail() throws Exception {
        when(mediaService.getMediaById(eq("m1"), eq(OTHER_EMAIL), eq(false)))
                .thenThrow(new vn.iotstar.utils.exceptions.wrapper.ForbiddenException(
                        "You do not have permission to access this media."));

        mockMvc.perform(get("/api/v1/media/m1")
                        .header("X-User-Email", OTHER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]"))
                .andExpect(status().isForbidden());
    }

    // ---------- GET /api/v1/media?ownerId= (admin-list, tách riêng khỏi /me để tránh IDOR) ----------

    @Test
    @DisplayName("khách ẩn danh gọi admin-list bị chặn 401")
    void anonymousCannotAccessAdminList() throws Exception {
        mockMvc.perform(get("/api/v1/media").param("ownerId", "owner-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("người dùng thường không được gọi admin-list dù biết ownerId của người khác")
    void plainUserCannotAccessAdminList() throws Exception {
        mockMvc.perform(get("/api/v1/media")
                        .param("ownerId", "owner-1")
                        .header("X-User-Email", OWNER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin gọi được admin-list")
    void adminCanAccessAdminList() throws Exception {
        when(mediaService.getMediaByOwnerId(eq("owner-1"), any()))
                .thenReturn(new PageImpl<>(List.of(media("m1", OWNER_EMAIL)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/media")
                        .param("ownerId", "owner-1")
                        .header("X-User-Email", ADMIN_EMAIL)
                        .header("X-User-Roles", "[ROLE_ADMIN]"))
                .andExpect(status().isOk());
    }

    // ---------- DELETE /api/v1/media/{id} ----------

    @Test
    @DisplayName("khách ẩn danh xoá media bị chặn 401")
    void anonymousCannotDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/media/m1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("chủ sở hữu xoá được media của mình")
    void ownerCanDeleteOwnMedia() throws Exception {
        mockMvc.perform(delete("/api/v1/media/m1")
                        .header("X-User-Email", OWNER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("admin xoá được media của người khác")
    void adminCanDeleteAnyMedia() throws Exception {
        mockMvc.perform(delete("/api/v1/media/m1")
                        .header("X-User-Email", ADMIN_EMAIL)
                        .header("X-User-Roles", "[ROLE_ADMIN]"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("người khác không phải chủ sở hữu, không phải admin bị chặn 403 khi xoá")
    void nonOwnerNonAdminIsForbiddenOnDelete() throws Exception {
        org.mockito.Mockito.doThrow(new vn.iotstar.utils.exceptions.wrapper.ForbiddenException(
                        "You do not have permission to access this media."))
                .when(mediaService).deleteMedia(eq("m1"), eq(OTHER_EMAIL), eq(false));

        mockMvc.perform(delete("/api/v1/media/m1")
                        .header("X-User-Email", OTHER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]"))
                .andExpect(status().isForbidden());
    }
}
