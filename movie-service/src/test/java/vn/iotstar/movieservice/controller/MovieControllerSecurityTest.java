package vn.iotstar.movieservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.iotstar.movieservice.config.security.FilterRegistrationConfig;
import vn.iotstar.movieservice.config.security.GatewayAuthFilter;
import vn.iotstar.movieservice.config.security.HeaderAuthenticationFilter;
import vn.iotstar.movieservice.config.security.RestAuthenticationHandlers;
import vn.iotstar.movieservice.config.security.SecurityConfig;
import vn.iotstar.movieservice.model.dto.MovieRequest;
import vn.iotstar.movieservice.model.dto.PageResponse;
import vn.iotstar.movieservice.service.MovieService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm chứng ranh giới phân quyền, thứ mà chỉ đọc code rất khó chắc chắn: đường dẫn quản trị
 * {@code /api/v1/movies/manage} nằm lồng bên trong prefix công khai {@code /api/v1/movies/**},
 * nên chỉ cần xếp sai thứ tự matcher là nó lộ ra ngoài.
 */
@WebMvcTest(controllers = MovieController.class)
@Import({SecurityConfig.class, GatewayAuthFilter.class, HeaderAuthenticationFilter.class,
        FilterRegistrationConfig.class, RestAuthenticationHandlers.class,
        MovieControllerSecurityTest.TestBeans.class})
@TestPropertySource(properties = "application.security.gateway-secret.enabled=false")
class MovieControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private MovieService movieService;

    @TestConfiguration
    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    private static final String ADMIN_EMAIL = "admin@novaplay.vn";
    private static final String USER_EMAIL = "user@novaplay.vn";

    private String validMovieJson() throws Exception {
        return objectMapper.writeValueAsString(new MovieRequest(
                "Phim Test", "Mô tả phim test", null, null, null, false, null, null, null));
    }

    // ---------- Đọc công khai ----------

    @Test
    @DisplayName("khách ẩn danh duyệt được catalog")
    void anonymousCanBrowse() throws Exception {
        when(movieService.browsePublished(any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("khách ẩn danh xem được chi tiết phim")
    void anonymousCanViewDetail() throws Exception {
        mockMvc.perform(get("/api/v1/movies/nha-ba-nu"))
                .andExpect(status().isOk());
    }

    // ---------- Ghi ----------

    @Test
    @DisplayName("khách ẩn danh tạo phim bị chặn 401")
    void anonymousCannotCreate() throws Exception {
        mockMvc.perform(post("/api/v1/movies")
                        .contentType("application/json")
                        .content(validMovieJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("người dùng thường tạo phim bị chặn 403")
    void plainUserCannotCreate() throws Exception {
        mockMvc.perform(post("/api/v1/movies")
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]")
                        .contentType("application/json")
                        .content(validMovieJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin tạo được phim")
    void adminCanCreate() throws Exception {
        mockMvc.perform(post("/api/v1/movies")
                        .header("X-User-Email", ADMIN_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER, ROLE_ADMIN]")
                        .contentType("application/json")
                        .content(validMovieJson()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("người dùng thường xoá phim bị chặn 403")
    void plainUserCannotDelete() throws Exception {
        mockMvc.perform(delete("/api/v1/movies/m1")
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]"))
                .andExpect(status().isForbidden());
    }

    // ---------- Đường dẫn quản trị lồng trong prefix công khai ----------

    @Test
    @DisplayName("khách ẩn danh KHÔNG xem được danh sách quản trị dù nó nằm dưới /api/v1/movies")
    void anonymousCannotAccessManageList() throws Exception {
        mockMvc.perform(get("/api/v1/movies/manage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("người dùng thường không xem được danh sách quản trị")
    void plainUserCannotAccessManageList() throws Exception {
        mockMvc.perform(get("/api/v1/movies/manage")
                        .header("X-User-Email", USER_EMAIL)
                        .header("X-User-Roles", "[ROLE_USER]"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin xem được danh sách quản trị")
    void adminCanAccessManageList() throws Exception {
        when(movieService.searchAll(any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/v1/movies/manage")
                        .header("X-User-Email", ADMIN_EMAIL)
                        .header("X-User-Roles", "[ROLE_ADMIN]"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("khách ẩn danh không xem được phim DRAFT qua đường dẫn quản trị")
    void anonymousCannotAccessManageDetail() throws Exception {
        mockMvc.perform(get("/api/v1/movies/manage/m1"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Giới hạn tham số phân trang ----------

    @Test
    @DisplayName("size vượt trần bị cắt về 100 chứ không kéo cả collection vào bộ nhớ")
    void oversizedPageIsCapped() throws Exception {
        when(movieService.browsePublished(any(), any(), any(),
                argThat(p -> p != null && p.getPageSize() == PageableFactory.MAX_PAGE_SIZE)))
                .thenReturn(new PageResponse<>(List.of(), 0, 100, 0, 0, true));

        mockMvc.perform(get("/api/v1/movies").param("size", "1000000"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sắp xếp theo field không nằm trong danh sách cho phép bị từ chối")
    void rejectsArbitrarySortField() throws Exception {
        mockMvc.perform(get("/api/v1/movies").param("sort", "description"))
                .andExpect(status().isBadRequest());
    }
}
