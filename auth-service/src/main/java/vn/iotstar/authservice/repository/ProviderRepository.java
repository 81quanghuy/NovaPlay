package vn.iotstar.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.authservice.model.entity.Providers;

import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Providers, String> {
    Optional<Providers> findByProviderName(String providerName);

    Optional<Providers> findByProviderNameAndUserId(String providerName, String userId);

    Optional<Providers> findByProviderNameAndProviderUrl(String providerName, String providerUrl);

    Optional<Providers> findByProviderNameAndUserIdAndProviderUrl(String providerName, String userId, String providerUrl);

    Optional<Providers> findByUserId(String userId);

    Optional<Providers> findByProviderUrl(String providerUrl);

    Optional<Providers> findByUserIdAndProviderUrl(String userId, String providerUrl);
}
