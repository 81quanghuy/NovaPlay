package vn.iotstar.authservice.service;


import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import vn.iostar.utils.constants.GenericResponse;
import vn.iotstar.authservice.model.dto.AccountDTO;

public interface IAccountService {
    ResponseEntity<GenericResponse> login(@Valid AccountDTO accountDTO) throws Exception;

    ResponseEntity<GenericResponse> userRegister(@Valid AccountDTO registerRequest);
}
