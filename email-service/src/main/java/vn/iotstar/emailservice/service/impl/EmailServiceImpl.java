package vn.iotstar.emailservice.service.impl;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import vn.iotstar.emailservice.model.dto.EmailDTO;
import vn.iotstar.emailservice.model.entity.Email;
import vn.iotstar.emailservice.repository.IEmailRepository;
import vn.iotstar.emailservice.service.IEmailService;
import vn.iotstar.emailservice.util.TopicName;
import vn.iotstar.utils.constants.GenericResponse;
import vn.iotstar.utils.dto.EmailRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static vn.iotstar.emailservice.mapper.EmailMapper.toDTO;
import static vn.iotstar.emailservice.util.MessageProperties.EMAIL_NOT_FOUND;
import static vn.iotstar.emailservice.util.MessageProperties.OTP_INVALID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements IEmailService {

    private final JavaMailSender mailSender;
    private final Random random = new Random();
    private final IEmailRepository emailRepository;
    private final TemplateEngine templateEngine;
    private final Environment env;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public static final int OTP_LENGTH = 6;

    public void sendOTP(EmailRequest emailRequest) {
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
            // Set expiration time to 5 minutes from now
            emailVerification.setExpirationTime(Instant.now().plus(5, ChronoUnit.MINUTES));
            emailRepository.save(emailVerification);
            ResponseEntity.ok(
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
    @Transactional
    public ResponseEntity<GenericResponse> verifyOTP(EmailDTO emailDTO) {
        final Instant now = Instant.now();
        final String email = emailDTO.email().trim().toLowerCase(Locale.ROOT);
        final String otp   = Objects.requireNonNullElse(emailDTO.otp(), "").trim();

        log.info("Verifying OTP for email: {}", email);

        if (!emailRepository.existsByRecipientEmailIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(GenericResponse.builder()
                            .success(false)
                            .message(EMAIL_NOT_FOUND)
                            .build());
        }
        Email recordOpt = emailRepository.findTopByRecipientEmailIgnoreCaseAndOtpAndExpirationTimeGreaterThanOrderByCreatedAtDesc(
                email, otp, now)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, OTP_INVALID));

        // 4) Idempotent: nếu đã verify trước đó thì trả về OK (hoặc tuỳ bạn muốn 400)
        if (Boolean.TRUE.equals(recordOpt.getIsVerified())) {
            return ResponseEntity.ok(GenericResponse.builder()
                    .success(true)
                    .message("Email already verified")
                    .result(recordOpt)
                    .build());
        }
        recordOpt.setIsVerified(true);
        emailRepository.save(recordOpt);

        kafkaTemplate.send(TopicName.VERIFY_OTP, email);
        return ResponseEntity.ok(GenericResponse.builder()
                .success(true)
                .message("OTP verified successfully")
                .result(recordOpt)
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
