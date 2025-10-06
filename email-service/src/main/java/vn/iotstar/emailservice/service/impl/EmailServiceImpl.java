package vn.iotstar.emailservice.service.impl;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import vn.iotstar.emailservice.model.dto.EmailDTO;
import vn.iotstar.emailservice.model.entity.Email;
import vn.iotstar.emailservice.repository.IEmailRepository;
import vn.iotstar.emailservice.service.IEmailService;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.utils.dto.EmailRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Random;

import static vn.iotstar.emailservice.mapper.EmailMapper.toDTO;
import static vn.iotstar.emailservice.util.MessageProperties.EMAIL_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements IEmailService {

    private final JavaMailSender mailSender;
    private final Random random = new Random();
    private final IEmailRepository emailRepository;
    private final TemplateEngine templateEngine;
    private final Environment env;

    public static final int OTP_LENGTH = 6;

    public ResponseEntity<GenericResponse>sendOTP(EmailRequest emailRequest) {
        log.info("Sending OTP email to: {}", emailRequest.recipientEmail());

        try {
            String otp = generateOtp();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(emailRequest.recipientEmail());

            Context context = new Context();
            context.setVariable("otpCode", otp);
            context.setVariable("verifyEmail", emailRequest);
            String mailContent = templateEngine.process("send-otp", context);

            helper.setText(mailContent, true);
            helper.setSubject("The verification token for NOVAPLAY");
            helper.setFrom(Objects.requireNonNull(env.getRequiredProperty("spring.mail.username")), "NovaPlay Team");
            mailSender.send(message);

            Email emailVerification = new Email();
            emailVerification.setRecipientEmail(emailRequest.recipientEmail());
            emailVerification.setOtp(otp);
            emailVerification.setExpirationTime(Instant.now().plus(5, ChronoUnit.MINUTES));
            emailRepository.save(emailVerification);
            return ResponseEntity.ok(
                    GenericResponse.builder()
                            .success(true)
                            .message("OTP đã được gửi thành công. Vui lòng kiểm tra email của bạn.")
                            .result(toDTO(emailVerification))
                            .build()
            );

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SMTP lỗi", e);
        }
    }

    @Override
    public ResponseEntity<GenericResponse> verifyOTP(EmailDTO emailDTO) {
        log.info("Verifying OTP for email: {}", emailDTO.email());

        Email emailVerify = emailRepository.findTopByRecipientEmailOrderByCreatedAtDesc(emailDTO.email())
                .orElseThrow(() -> new RuntimeException(EMAIL_NOT_FOUND));
        if (!emailVerify.getOtp().equals(emailDTO.otp())) {
            return ResponseEntity.badRequest().body(GenericResponse.builder()
                    .success(false)
                    .message("Invalid OTP")
                    .build());
        }
        if (emailVerify.getExpirationTime().isBefore(Instant.now())) {
            return ResponseEntity.badRequest().body(GenericResponse.builder()
                    .success(false)
                    .message("OTP has expired")
                    .build());
        }

        emailVerify.setIsVerified(true);
        emailRepository.save(emailVerify);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message("OTP verified successfully")
                .result(emailDTO)
                .build());
    }

    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }
}
