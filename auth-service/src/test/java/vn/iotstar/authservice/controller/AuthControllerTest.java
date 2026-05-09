package vn.iotstar.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import vn.iotstar.authservice.model.dto.ChangePasswordRequest;
import vn.iotstar.authservice.model.dto.RefreshTokenRequest;
import vn.iotstar.authservice.model.entity.User;
import vn.iotstar.authservice.service.AuthService;
import vn.iotstar.authservice.service.OtpService;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for {@link AuthController}.
 * Uses a custom argument resolver to inject the {@link User} principal,
 * simulating how {@link org.springframework.security.core.annotation.AuthenticationPrincipal}
 * works in a full Spring context.
 */
@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @Mock
    private OtpService otpService;

    @Mock
    private vn.iotstar.authservice.service.JwtService jwtService;

    @InjectMocks
    private AuthController authController;

    private User dummyUser;

    @BeforeEach
    void setUp() {
        dummyUser = new User();
        dummyUser.setEmail("tester@gmail.com");

        // Custom resolver: any parameter of type User is resolved to dummyUser
        HandlerMethodArgumentResolver userResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return User.class.isAssignableFrom(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return dummyUser;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setCustomArgumentResolvers(userResolver)
                .build();
    }

    // =========================================================================
    // TC-01: POST /api/v1/auth/logout – happy path
    // Verifies that the controller correctly extracts refreshToken + email
    // from the request body and the @AuthenticationPrincipal User.
    // =========================================================================
    @Test
    void logout_withUserPrincipal_returnsNoContent() throws Exception {
        doNothing().when(authService).logout(anyString(), anyString());

        String body = objectMapper.writeValueAsString(
                new RefreshTokenRequest("dummy-refresh-token"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(authService, times(1)).logout(eq("dummy-refresh-token"), eq("tester@gmail.com"));
    }

    // =========================================================================
    // TC-02: POST /api/v1/auth/change-password – happy path
    // Verifies the controller wires the ChangePasswordRequest DTO and
    // user email correctly into the service layer.
    // =========================================================================
    @Test
    void changePassword_withValidRequest_returnsOk() throws Exception {
        doNothing().when(authService).changePassword(any(ChangePasswordRequest.class), anyString());

        ChangePasswordRequest req = new ChangePasswordRequest(
                "OldP@ssw0rd!", "NewP@ssw0rd!", "NewP@ssw0rd!");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(authService, times(1)).changePassword(any(ChangePasswordRequest.class), eq("tester@gmail.com"));
    }
}
