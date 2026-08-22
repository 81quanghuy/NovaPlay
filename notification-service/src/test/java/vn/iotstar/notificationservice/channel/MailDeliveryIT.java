package vn.iotstar.notificationservice.channel;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import vn.iotstar.notificationservice.channel.impl.EmailChannel;
import vn.iotstar.notificationservice.config.observability.NotificationMetrics;
import vn.iotstar.notificationservice.model.dto.NotificationRequest;
import vn.iotstar.notificationservice.model.enums.NotificationType;
import vn.iotstar.notificationservice.service.MailDeliveryException;
import vn.iotstar.notificationservice.service.impl.MailSenderImpl;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gửi email qua một máy chủ SMTP thật (GreenMail chạy trong tiến trình).
 * <p>
 * Unit test dùng {@code JavaMailSenderImpl} giả chỉ kiểm được nội dung ta đưa vào MimeMessage.
 * Test này đi hết đường: mã hoá MIME, header, và việc chuỗi UTF-8 tiếng Việt có sống sót qua
 * SMTP hay không — những thứ chỉ sai khi gặp máy chủ thật.
 * <p>
 * GreenMail chạy in-process nên test này không cần Docker, khác với {@code NotificationRepositoryIT}.
 */
class MailDeliveryIT {

    private static final String FRONTEND = "https://novaplay.test";
    private static final String MAIL_FROM = "no-reply@novaplay.test";

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private EmailChannel channel;

    @BeforeEach
    void setUp() {
        var javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setHost("127.0.0.1");
        javaMailSender.setPort(greenMail.getSmtp().getPort());

        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);

        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(messageSource);

        var mailSender = new MailSenderImpl(javaMailSender, engine, messageSource,
                new NotificationMetrics(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(mailSender, "mailFrom", MAIL_FROM);
        ReflectionTestUtils.setField(mailSender, "frontendBaseUrl", FRONTEND);
        channel = new EmailChannel(mailSender);
    }

    private static NotificationRequest otp(Locale locale) {
        return NotificationRequest.builder()
                .dedupKey("evt-1")
                .userEmail("user@novaplay.dev")
                .type(NotificationType.OTP)
                .locale(locale)
                .variables(Map.of("otp", "123456", "expireMinutes", "7"))
                .build();
    }

    @Test
    @DisplayName("email tiếng Việt tới nơi với dấu còn nguyên vẹn")
    void email_tieng_viet_khong_bi_hong_dau() throws Exception {
        channel.send(otp(Locale.forLanguageTag("vi-VN")));

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        MimeMessage received = greenMail.getReceivedMessages()[0];

        assertThat(received.getSubject()).isEqualTo("Mã xác thực OTP của bạn");
        assertThat(received.getAllRecipients()[0].toString()).isEqualTo("user@novaplay.dev");

        String body = GreenMailUtil.getBody(received);
        // Nội dung được mã hoá quoted-printable trên đường truyền, phải giải mã trước khi so khớp.
        String decoded = new String(jakarta.mail.internet.MimeUtility
                .decode(new java.io.ByteArrayInputStream(body.getBytes("ISO-8859-1")), "quoted-printable")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(decoded).contains("123456");
        assertThat(decoded).contains("Mã có hiệu lực trong 7 phút");
        assertThat(decoded).contains("https://novaplay.test/auth-email?email=user@novaplay.dev");
    }

    @Test
    @DisplayName("email tiếng Anh dùng đúng bundle _en")
    void email_tieng_anh() throws Exception {
        channel.send(otp(Locale.forLanguageTag("en")));

        assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
        assertThat(greenMail.getReceivedMessages()[0].getSubject())
                .isEqualTo("Your OTP verification code");
    }

    @Test
    @DisplayName("SMTP không tới được thì báo MailDeliveryException, không gửi ra email nào")
    void smtp_khong_toi_duoc() {
        var deadSender = new JavaMailSenderImpl();
        deadSender.setHost("127.0.0.1");
        deadSender.setPort(1); // không có gì lắng nghe ở đây

        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(messageSource);

        var deadMailSender = new MailSenderImpl(deadSender, engine, messageSource,
                new NotificationMetrics(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(deadMailSender, "mailFrom", MAIL_FROM);
        ReflectionTestUtils.setField(deadMailSender, "frontendBaseUrl", FRONTEND);
        var deadChannel = new EmailChannel(deadMailSender);

        assertThatThrownBy(() -> deadChannel.send(otp(Locale.forLanguageTag("vi-VN"))))
                .isInstanceOf(MailDeliveryException.class);

        // Thất bại ở tầng transport nghĩa là thư chưa rời đi — dispatcher được phép thử lại.
        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }
}
