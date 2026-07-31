package vn.iotstar.notificationservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.iotstar.notificationservice.config.security.FilterRegistrationConfig;
import vn.iotstar.notificationservice.config.security.GatewayAuthFilter;
import vn.iotstar.notificationservice.config.security.HeaderAuthenticationFilter;
import vn.iotstar.notificationservice.config.security.RestAuthenticationHandlers;
import vn.iotstar.notificationservice.config.security.SecurityConfig;
import vn.iotstar.notificationservice.model.dto.NotificationDTO;
import vn.iotstar.notificationservice.service.NotificationQueryService;
import vn.iotstar.utils.exceptions.wrapper.ResourceNotFoundException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng ranh giới dữ liệu: mọi endpoint đều phải lọc theo đúng người gọi.
 * <p>
 * Đây là rủi ro chính của service này — controller nhận danh tính từ header do gateway inject,
 * nên chỉ cần một endpoint quên truyền {@code authentication.getName()} xuống service là người
 * dùng đọc được thông báo của người khác.
 */
@WebMvcTest(controllers = NotificationController.class)
@Import({SecurityConfig.class, GatewayAuthFilter.class, HeaderAuthenticationFilter.class,
        FilterRegistrationConfig.class, RestAuthenticationHandlers.class,
        NotificationControllerSecurityTest.TestBeans.class})
@TestPropertySource(properties = "application.security.gateway-secret.enabled=false")
class NotificationControllerSecurityTest {

    private static final String ALICE = "alice@novaplay.vn";
    private static final String BOB = "bob@novaplay.vn";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private NotificationQueryService queryService;

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    @DisplayName("khách ẩn danh nhận 401 chứ không phải 403 — frontend cần 401 để refresh token")
    void an_danh_nhan_401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("service chỉ được truy vấn bằng email của chính người gọi")
    void loc_theo_dung_nguoi_goi() throws Exception {
        Page<NotificationDTO> empty = new PageImpl<>(List.of(), Pageable.ofSize(20), 0);
        when(queryService.list(eq(ALICE), any(), anyBoolean())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/notifications").header("X-User-Email", ALICE))
                .andExpect(status().isOk());

        verify(queryService).list(eq(ALICE), any(), eq(false));
    }

    @Test
    @DisplayName("Bob không đọc được thông báo của Alice: nhận 404, không phải 403")
    void khong_doc_duoc_thong_bao_nguoi_khac() throws Exception {
        // Service ném ResourceNotFoundException khi id không khớp userEmail. Trả 404 thay vì 403
        // là cố ý: 403 sẽ tiết lộ rằng thông báo đó tồn tại và thuộc về người khác.
        when(queryService.markRead(eq(BOB), eq("notif-cua-alice")))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy thông báo"));

        mockMvc.perform(patch("/api/v1/notifications/notif-cua-alice/read")
                        .header("X-User-Email", BOB))
                .andExpect(status().isNotFound());
    }

    @Test
    void dem_chua_doc_tra_ve_so_cua_chinh_nguoi_goi() throws Exception {
        when(queryService.unreadCount(ALICE)).thenReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("X-User-Email", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.unreadCount").value(3));
    }

    @Test
    @DisplayName("size vượt trần bị cắt xuống 100 thay vì kéo cả collection vào bộ nhớ")
    void size_bi_cat_xuong_tran() throws Exception {
        Page<NotificationDTO> empty = new PageImpl<>(List.of(), Pageable.ofSize(20), 0);
        when(queryService.list(eq(ALICE), any(), anyBoolean())).thenReturn(empty);

        mockMvc.perform(get("/api/v1/notifications")
                        .param("size", "100000")
                        .header("X-User-Email", ALICE))
                .andExpect(status().isOk());

        verify(queryService).list(eq(ALICE),
                org.mockito.ArgumentMatchers.argThat(p -> p.getPageSize() == 100), eq(false));
    }

    @Test
    void page_am_bi_tu_choi() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("page", "-1").header("X-User-Email", ALICE))
                .andExpect(status().isBadRequest());
    }
}
