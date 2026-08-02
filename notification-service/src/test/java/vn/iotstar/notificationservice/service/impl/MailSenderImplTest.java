package vn.iotstar.notificationservice.service.impl;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import vn.iotstar.notificationservice.config.observability.NotificationMetrics;
import vn.iotstar.notificationservice.model.dto.NotificationRequest;
import vn.iotstar.notificationservice.model.enums.NotificationType;
import vn.iotstar.notificationservice.service.MailDeliveryException;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailSenderImplTest {

    private static final String FRONTEND = "https://novaplay.test";

    private AtomicReference<MimeMessage> sent;
    private MailSenderImpl mailSender;
    private RecordingMailSender javaMailSender;
    private SpringTemplateEngine templateEngine;
    private ResourceBundleMessageSource messageSource;

    /**
     * Bắt lại MimeMessage thay vì mock JavaMailSender: khẳng định "đã gọi send()" không nói lên
     * được tiêu đề, người nhận hay nội dung có đúng không — mà đó mới là thứ dễ sai.
     */
    private static class RecordingMailSender extends JavaMailSenderImpl {
        private final AtomicReference<MimeMessage> captured;
        private RuntimeException failure;

        RecordingMailSender(AtomicReference<MimeMessage> captured) {
            this.captured = captured;
            setJavaMailProperties(new Properties());
        }

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void send(MimeMessage mimeMessage) {
            if (failure != null) {
                throw failure;
            }
            captured.set(mimeMessage);
        }
    }

    @BeforeEach
    void setUp() {
        sent = new AtomicReference<>();
        javaMailSender = new RecordingMailSender(sent);

        messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        // Khớp với spring.messages.fallback-to-system-locale=false ở application.yml.
        messageSource.setFallbackToSystemLocale(false);

        // SpringTemplateEngine chứ không phải TemplateEngine thuần: đây là bean mà Boot tự cấu
        // hình ở production, và cũng là thứ duy nhất nối được cú pháp #{...} trong template với
        // MessageSource của Spring.
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        templateEngine.setTemplateEngineMessageSource(messageSource);

        var metrics = new NotificationMetrics(new SimpleMeterRegistry());
    }

    private static NotificationRequest otpRequest(Locale locale) {
        return NotificationRequest.builder()
                .dedupKey("evt-1")
                .userEmail("user@novaplay.dev")
                .type(NotificationType.OTP)
                .locale(locale)
                .variables(Map.of("otp", "123456", "expireMinutes", "7"))
                .build();
    }

    @Test
    @DisplayName("tiêu đề được lấy từ bundle theo đúng locale")
    void tieu_de_theo_locale() throws Exception {
        mailSender.send(otpRequest(Locale.forLanguageTag("en")));
        assertThat(sent.get().getSubject()).isEqualTo("Your OTP verification code");

        mailSender.send(otpRequest(Locale.forLanguageTag("vi-VN")));
        assertThat(sent.get().getSubject()).isEqualTo("Mã xác thực OTP của bạn");
    }

    @Test
    @DisplayName("expireMinutes được render vào nội dung — trước khi gộp, tham số này bị bỏ quên")
    void expire_minutes_duoc_render() throws Exception {
        mailSender.send(otpRequest(Locale.forLanguageTag("en")));

        String body = (String) sent.get().getContent();
        assertThat(body).contains("123456");
        assertThat(body).contains("valid for 7 minutes");
    }

    @Test
    @DisplayName("URL frontend đến từ cấu hình, không còn hardcode localhost:3000")
    void url_frontend_tu_cau_hinh() throws Exception {
        mailSender.send(otpRequest(Locale.forLanguageTag("vi-VN")));

        String body = (String) sent.get().getContent();
        assertThat(body).contains(FRONTEND + "/auth-email?email=user@novaplay.dev");
        assertThat(body).doesNotContain("localhost:3000");
    }

    @Test
    void nguoi_gui_va_nguoi_nhan_dung() throws Exception {
        mailSender.send(otpRequest(Locale.forLanguageTag("vi-VN")));

        assertThat(sent.get().getFrom()[0].toString()).contains("no-reply@novaplay.test");
        assertThat(sent.get().getAllRecipients()[0].toString()).isEqualTo("user@novaplay.dev");
    }

    @Test
    @DisplayName("lỗi SMTP được bọc thành MailDeliveryException để dispatcher phân loại được")
    void loi_smtp_duoc_boc() {
        javaMailSender.failWith(new MailSendException("connection refused"));

        assertThatThrownBy(() -> mailSender.send(otpRequest(Locale.forLanguageTag("vi-VN"))))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessageContaining("SMTP từ chối email");
    }

    @Test
    void thieu_email_nguoi_nhan_thi_bao_loi() {
        NotificationRequest noEmail = NotificationRequest.builder()
                .dedupKey("evt-1")
                .type(NotificationType.OTP)
                .locale(Locale.forLanguageTag("vi-VN"))
                .variables(Map.of("otp", "123456"))
                .build();

        assertThatThrownBy(() -> mailSender.send(noEmail))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("timer luôn được dừng kể cả khi gửi lỗi")
    void timer_luon_duoc_dung() {
        var registry = new SimpleMeterRegistry();
        var failing = new RecordingMailSender(new AtomicReference<>());
        failing.failWith(new MailSendException("boom"));

        Timer timer = registry.find("notification.email.send").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }
}
