package vn.iotstar.authservice.service;


import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import vn.iostar.utils.constants.GenericResponse;
import vn.iotstar.authservice.model.dto.AccountDTO;

import java.io.UnsupportedEncodingException;

public interface IAccountService {
    ResponseEntity<GenericResponse> login(@Valid AccountDTO accountDTO) throws Exception;
    ResponseEntity<GenericResponse> sendOTP(@Valid AccountDTO registerRequest)
            throws MessagingException, UnsupportedEncodingException;
    ResponseEntity<GenericResponse> register(@Valid AccountDTO registerRequest);
}
