package vn.iotstar.emailservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import vn.iotstar.emailservice.service.IEmailService;
import vn.iotstar.emailservice.util.MessageProperties;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements IEmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final Environment env;
    @Override
    public void sendOTP(String to, String otp, String expireMinutes, String localeTag) {
        try {
            var msg = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(Objects.requireNonNull(env.getRequiredProperty("spring.mail.username")), MessageProperties.APP_NAME);
            helper.setTo(to);
            helper.setSubject(localeTag != null && localeTag.startsWith("vi")
                    ? MessageProperties.OTP_CODE_VI
                    : MessageProperties.OTP_CODE_EN);

            Context context = new Context();
            context.setVariable("otpCode", otp);
            context.setVariable("verifyEmail", to);
            String mailContent = templateEngine.process("send-otp", context);
            helper.setText(mailContent, true);

            mailSender.send(msg);
            log.info("Sent OTP email to {}, locale={}", maskEmail(to), localeTag);
        } catch (Exception ex) {
            throw new MailSendException("Failed to send OTP email", ex);
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
    }
}
