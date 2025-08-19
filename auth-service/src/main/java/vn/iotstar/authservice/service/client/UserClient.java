package vn.iotstar.authservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.iotstar.utils.constants.GenericResponse;

@Component
@FeignClient(name = "user-service", contextId = "userClientService", path = "/api/v1")
public interface UserClient {

    @PostMapping("/user/create")
    ResponseEntity<GenericResponse> createUser(@RequestBody AccountDTO pAccountDTO);
}
