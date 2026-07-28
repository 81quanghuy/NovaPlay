package vn.iotstar.movieservice.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GatewayAuthFilterTest {

    private static final String SECRET = "s3cr3t-value";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GatewayAuthFilter enabledFilter() {
        return new GatewayAuthFilter(true, SECRET, objectMapper);
    }

    @Test
    @DisplayName("request không có header bị chặn bằng 403")
    void rejectsRequestWithoutHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        enabledFilter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("secret sai bị chặn bằng 403")
    void rejectsWrongSecret() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(GatewayAuthFilter.X_GATEWAY_AUTH, "doan-mo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        enabledFilter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("secret đúng được đi tiếp")
    void allowsCorrectSecret() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(GatewayAuthFilter.X_GATEWAY_AUTH, SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        enabledFilter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("health probe được miễn kiểm tra")
    void exemptsHealthProbes() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/actuator/health/readiness");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // Orchestrator gọi các endpoint này trực tiếp, không qua gateway.
        enabledFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("khi tắt thì mọi request đều đi qua")
    void passesEverythingWhenDisabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new GatewayAuthFilter(false, "", objectMapper).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("bật mà không cấu hình secret thì fail-fast lúc khởi động")
    void failsFastWhenEnabledWithoutSecret() {
        // Khởi động im lặng với secret rỗng sẽ tạo ra một service tưởng là đã được bảo vệ.
        assertThatThrownBy(() -> new GatewayAuthFilter(true, "", objectMapper))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gateway-secret");
    }
}
