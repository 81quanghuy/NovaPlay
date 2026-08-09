package vn.iotstar.authservice.config.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import vn.iotstar.authservice.repository.RoleRepository;
import vn.iotstar.authservice.util.RoleName;

@Component
@RequiredArgsConstructor
public class DbSeedHealthIndicator implements HealthIndicator {

    private final RoleRepository roleRepository;

    @Override
    public Health health() {
        boolean hasUserRole = roleRepository.findByRoleName(RoleName.USER).isPresent();
        if (hasUserRole) {
            return Health.up().withDetail("db-seed", "OK").build();
        }
        return Health.down()
                .withDetail("db-seed", "MISSING: Role USER not found - run seed script")
                .build();
    }
}
