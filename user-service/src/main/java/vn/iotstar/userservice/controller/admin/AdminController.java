package vn.iotstar.userservice.controller.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/users/admin")
public class AdminController {

    @GetMapping("/hello")
    public String hello() {
        log.info("Admin endpoint accessed");
        return "Hello from Admin Controller";
    }
}
