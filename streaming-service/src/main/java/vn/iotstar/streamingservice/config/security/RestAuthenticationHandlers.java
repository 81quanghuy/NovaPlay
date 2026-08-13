package vn.iotstar.streamingservice.config.security;

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
