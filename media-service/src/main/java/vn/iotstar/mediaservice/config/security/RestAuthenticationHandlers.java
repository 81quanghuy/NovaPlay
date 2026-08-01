package vn.iotstar.mediaservice.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Trả lỗi xác thực/phân quyền dưới dạng JSON đúng mã trạng thái.
 * <p>
 * Không có lớp này, Spring Security dùng {@code Http403ForbiddenEntryPoint} mặc định (vì service
 * không cấu hình form login hay http basic), nên request KHÔNG có danh tính cũng nhận 403 thay vì
 * 401. Khác biệt đó quan trọng với client: 401 nghĩa là "hãy đăng nhập lại / làm mới token", còn
 * 403 nghĩa là "đã biết bạn là ai nhưng bạn không có quyền" — frontend chỉ kích hoạt luồng refresh
 * token khi thấy 401.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationHandlers {

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> write(request, response,
                HttpStatus.UNAUTHORIZED, "Yêu cầu này cần được xác thực");
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> write(request, response,
                HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
    }

    /**
     * Dựng JSON bằng ObjectNode thay vì serialize một POJO có kiểu java.time: các handler này chạy
     * ngoài chuỗi MVC nên không được phụ thuộc vào việc ObjectMapper được inject đã đăng ký
     * JavaTimeModule hay chưa. Đây là đường trả lỗi — nó phải luôn ghi ra được phản hồi.
     */
    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("success", false);
        body.put("message", message);
        body.put("statusCode", status.value());
        body.put("timestamp", Instant.now().toString());
        body.put("path", request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
