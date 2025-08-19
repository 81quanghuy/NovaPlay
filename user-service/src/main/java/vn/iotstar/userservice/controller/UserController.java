package vn.iotstar.userservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/public")
    public String publicApi() {
        return "Hello PUBLIC 👋";
    }

    @GetMapping("/secure")
    public String secure(@AuthenticationPrincipal Jwt jwt) {
        return StringTemplate.STR."Hello SECURE, user: \{jwt.getClaimAsString("preferred_username")}";
    }
}
