package vn.iotstar.utils.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("auditAwareImpl")
public class AuditAwareImpl implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() {
        var auth = SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.of("system"); // cho batch/test
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String u = jwt.getClaimAsString("preferred_username");
            if (u == null || u.isBlank()) u = jwt.getClaimAsString("email");
            if (u == null || u.isBlank()) u = jwt.getSubject(); // sub
            return Optional.ofNullable(u);
        }
        return Optional.ofNullable(auth.getName());
    }
}
