package vn.iotstar.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.authservice.model.entity.Role;
import vn.iotstar.authservice.util.RoleName;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /**
     * Finds a role by its RoleName enum.
     * @param roleName The name of the role to find.
     * @return an Optional containing the found Role.
     */
    Optional<Role> findByRoleName(RoleName roleName);
}