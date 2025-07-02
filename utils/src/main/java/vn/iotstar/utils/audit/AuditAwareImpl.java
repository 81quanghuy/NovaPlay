package vn.iotstar.utils.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static vn.iotstar.utils.constants.AppConst.AUDIT_AWARE_AUDITOR;

@Component("auditAwareImpl")
public class AuditAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
       return Optional.of(AUDIT_AWARE_AUDITOR);
    }
}
