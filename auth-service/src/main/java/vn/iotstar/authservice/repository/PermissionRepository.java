package vn.iotstar.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.authservice.model.entity.Permission;

import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Integer> {

    /**
     * Finds a permission by its unique name.
     * @param name The unique name to search for.
     * @return an Optional containing the found Permission.
     */
    Optional<Permission> findByName(String name);
}