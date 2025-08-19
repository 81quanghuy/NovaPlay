package vn.iotstar.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.authservice.model.entity.Provider;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, UUID> {

    /**
     * Finds a provider link by the provider's name and the user's ID from that provider.
     * This is the core method for the OAuth2 login flow.
     * @param providerName The name of the provider (e.g., 'GOOGLE').
     * @param providerUrl The user's unique identifier from the provider.
     * @return an Optional containing the found Provider link.
     */
    Optional<Provider> findByProviderNameAndProviderUrl(String providerName, String providerUrl);
}