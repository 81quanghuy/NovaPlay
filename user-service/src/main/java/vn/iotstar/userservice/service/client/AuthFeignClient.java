package vn.iotstar.userservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import vn.iotstar.userservice.model.dto.LoginRequest;

@FeignClient(name="auth-service", fallback = AuthFallback.class)
public interface AuthFeignClient {

    @GetMapping(value = "/api/fetch",consumes = "application/json")
    public ResponseEntity<LoginRequest> fetchCardDetails(@RequestHeader("eazybank-correlation-id")
                                                         String correlationId, @RequestParam String mobileNumber);

}
