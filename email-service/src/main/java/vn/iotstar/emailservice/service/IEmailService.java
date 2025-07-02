package vn.iotstar.emailservice.service;


import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.emailservice.model.dto.EmailDTO;
import vn.iotstar.emailservice.model.entity.Email;

public interface IEmailService {

    Email findByEmail(String email);

    Email save(Email email);

    ResponseEntity<GenericResponse> sendOTP(@Valid EmailDTO pRegisterRequest);
}
