package vn.iotstar.userservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RefreshScope // Annotation quan trọng để bean này có thể được refresh
public class MessageController {

    @Value("${app.message}") // Lấy giá trị từ file config trên Git
    private String message;

    @GetMapping("/api/v1/users/message")
    public String getMessage() {
        return this.message;
    }
}