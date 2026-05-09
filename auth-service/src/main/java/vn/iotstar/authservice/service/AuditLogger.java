package vn.iotstar.authservice.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuditLogger {

    public void loginSuccess(String userId, String email, String ip) {
        log.info("event=auth.login.success userId={} email={} ip={} traceId={}",
                userId, email, ip, MDC.get("traceId"));
    }

    public void loginFailure(String emailOrUsername, String reason, String ip) {
        log.warn("event=auth.login.failure identity={} reason={} ip={} traceId={}",
                emailOrUsername, reason, ip, MDC.get("traceId"));
    }

    public void passwordChanged(String userId, String email) {
        log.info("event=auth.password.changed userId={} email={} traceId={}",
                userId, email, MDC.get("traceId"));
    }

    public void passwordReset(String email) {
        log.info("event=auth.password.reset email={} traceId={}",
                email, MDC.get("traceId"));
    }

    public void accountActivated(String userId, String email) {
        log.info("event=auth.account.activated userId={} email={} traceId={}",
                userId, email, MDC.get("traceId"));
    }

    public void tokenRevoked(String userId, String tokenType) {
        log.info("event=auth.token.revoked userId={} tokenType={} traceId={}",
                userId, tokenType, MDC.get("traceId"));
    }

    public void otpRequested(String email, String purpose) {
        log.info("event=auth.otp.requested email={} purpose={} traceId={}",
                email, purpose, MDC.get("traceId"));
    }
}
