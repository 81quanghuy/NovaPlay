package vn.iotstar.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.iotstar.authservice.model.entity.Email;
import vn.iotstar.authservice.repository.EmailRepository;
import vn.iotstar.authservice.service.IEmailService;

@Service
@RequiredArgsConstructor
public class EmailService implements IEmailService {

    private final EmailRepository emailRepository;
    @Override
    public Email findByEmail(String email) {
        return emailRepository.findByEmail(email);
    }

    @Override
    public Email save(Email email) {
        return emailRepository.save(email);
    }
}
