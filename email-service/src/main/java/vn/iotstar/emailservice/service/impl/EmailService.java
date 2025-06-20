package vn.iotstar.emailservice.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import vn.iostar.utils.constants.GenericResponse;
import vn.iotstar.emailservice.model.dto.EmailDTO;
import vn.iotstar.emailservice.model.entity.Email;
import vn.iotstar.emailservice.repository.EmailRepository;
import vn.iotstar.emailservice.service.IEmailService;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService implements IEmailService {

    private final EmailRepository emailRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final Environment env;
    public static final int OTP_LENGTH = 6; // Length of the OTP

    @Override
    public Email findByEmail(String email) {
        return emailRepository.findByEmail(email);
    }

    @Override
    public Email save(Email email) {
        return emailRepository.save(email);
    }

    @Override
    public ResponseEntity<GenericResponse> sendOTP(EmailDTO pRegisterRequest) {
        return null;
    }

    /**
     * Send OTP to the user's email
     *
     * @param pEmail User's email address
     */
    private void sendOTPEmail(String pEmail) throws MessagingException, UnsupportedEncodingException {
        String otp = generateOtp();
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setTo(pEmail);

        // Load Thymeleaf template
        Context context = new Context();
        context.setVariable("otpCode", otp);
        context.setVariable("verifyEmail", pEmail);
        String mailContent = templateEngine.process("send-otp", context);

        helper.setText(mailContent, true);
        helper.setSubject("The verification token for NOVAPLAY");
        helper.setFrom(Objects.requireNonNull(env.getProperty("spring.mail.username")), "NovaPlay Team");
        mailSender.send(message);

        LocalDateTime expirationTime = LocalDateTime.now().plusMinutes(5);
        Email emailVerification = new Email();
        emailVerification.setEmail(pEmail);
        emailVerification.setOtp(otp);
        emailVerification.setExpirationTime(expirationTime);
        save(emailVerification);
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }
}
