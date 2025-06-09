package vn.iotstar.userservice.service;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import vn.iostar.utils.constants.GenericResponse;
import vn.iotstar.userservice.model.dto.UpdateUserRequest;
import vn.iotstar.userservice.model.dto.UserDTO;
import vn.iotstar.userservice.model.entity.User;

public interface IUserSerivce {

    ResponseEntity<GenericResponse> createUser(@Valid UserDTO UserDto) ;

    ResponseEntity<GenericResponse> updateUser(@Valid UpdateUserRequest pUserDTO);

    ResponseEntity<GenericResponse> getUserById(String userId);

    ResponseEntity<GenericResponse> deleteUserById(String userId);
}
