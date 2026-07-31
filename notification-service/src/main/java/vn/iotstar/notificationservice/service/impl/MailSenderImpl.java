package vn.iotstar.notificationservice.service.impl;

import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import vn.iotstar.notificationservice.config.observability.NotificationMetrics;
import vn.iotstar.notificationservice.model.dto.NotificationRequest;
import vn.iotstar.notificationservice.model.enums.NotificationType;
import vn.iotstar.notificationservice.service.MailDeliveryException;
import vn.iotstar.notificationservice.service.MailSender;

import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
public class MailSenderImpl implements MailSender {

    private static final String APP_NAME = "NovaPlay Team";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final MessageSource messageSource;
    private final NotificationMetrics metrics;
    private final String mailFrom;
    private final String frontendBaseUrl;

    public MailSenderImpl(JavaMailSender mailSender, TemplateEngine templateEngine,
                           MessageSource messageSource, NotificationMetrics metrics,
                           @Value("${application.mail.from:${spring.mail.username}}") String mailFrom,
                           @Value("${application.frontend.base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.messageSource = messageSource;
        this.metrics = metrics;
        this.mailFrom = mailFrom;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void send(NotificationRequest request) {
        Timer.Sample sample = Timer.start();
        try {
            var msg = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(mailFrom, APP_NAME);
            helper.setTo(Objects.requireNonNull(request.userEmail(), "userEmail bắt buộc"));
            helper.setSubject(subjectFor(request));
            helper.setText(renderBody(request), true);
            mailSender.send(msg);
            log.info("Đã gửi email loại={} tới={} locale={}",
                    request.type(), maskEmail(request.userEmail()), request.locale());
        } catch (java.io.UnsupportedEncodingException | jakarta.mail.MessagingException ex) {
            // Lỗi dựng MimeMessage — chưa từng chạm tới máy chủ SMTP.
            throw new MailDeliveryException("Không dựng được email", ex);
        } catch (MailException ex) {
            // JavaMailSender ném ở tầng transport trước khi máy chủ SMTP chấp nhận toàn bộ thư.
            throw new MailDeliveryException("SMTP từ chối email", ex);
        } finally {
            sample.stop(metrics.emailSendTimer());
        }
    }

    private String subjectFor(NotificationRequest request) {
        String override = request.variable("subject");
        if (override != null && !override.isBlank()) {
            return override;
        }
        return messageSource.getMessage(request.type().subjectKey(), null, request.locale());
    }

    private String renderBody(NotificationRequest request) {
        Locale locale = request.locale();
        Context context = new Context(locale);
        NotificationType type = request.type();

        switch (type) {
            case OTP -> {
                context.setVariable("otpCode", request.variable("otp"));
                context.setVariable("expireMinutes", request.variableOrDefault("expireMinutes", "5"));
                context.setVariable("verifyEmail", request.userEmail());
                context.setVariable("frontendBaseUrl", frontendBaseUrl);
            }
            case ACCOUNT_ACTIVATED -> {
                context.setVariable("username", request.variableOrDefault("username", request.userEmail()));
                context.setVariable("frontendBaseUrl", frontendBaseUrl);
            }
            case PASSWORD_RESET -> context.setVariable("url", request.variable("url"));
            case GENERIC -> {
                context.setVariable("title", request.variableOrDefault("title", ""));
                context.setVariable("body", request.variableOrDefault("body", ""));
            }
        }
        return templateEngine.process(type.templateName(), context);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
    }
}
