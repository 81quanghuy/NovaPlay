package vn.iotstar.userservice.service.client;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import vn.iotstar.userservice.model.dto.LoginRequest;

@Component
public class AuthFallback implements AuthFeignClient{

    @Override
    public ResponseEntity<LoginRequest> fetchCardDetails(String correlationId, String mobileNumber) {
        return null;
    }

}
