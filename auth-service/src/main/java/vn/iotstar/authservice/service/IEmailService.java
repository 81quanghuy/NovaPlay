package vn.iotstar.authservice.service;

import vn.iotstar.authservice.model.entity.Email;

public interface IEmailService {

    Email findByEmail(String email);

    Email save(Email email);
}
