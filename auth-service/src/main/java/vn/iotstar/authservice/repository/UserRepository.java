package vn.iotstar.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.authservice.model.entity.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by their email address.
     * @param email The email address to search for.
     * @return an Optional containing the found User.
     */
    Optional<User> findByEmail(String email);

    /**
     * Finds a user by their username.
     * @param username The username to search for.
     * @return an Optional containing the found User.
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a user by their email or username.
     * @param email The email address to search for.
     * @param username The username to search for.
     * @return an Optional containing the found User.
     */
    Optional<User> findByEmailOrUsername(String email, String username);
    /**
     * Checks if a user exists with the given email.
     * @param email The email address to check.
     * @return true if the email already exists, false otherwise.
     */
    boolean existsByEmail(String email);

    /**
     * Checks if a user exists with the given username.
     * @param username The username to check.
     * @return true if the username already exists, false otherwise.
     */
    boolean existsByUsername(String username);

    /**
     * Checks if a user exists with the given email and username.
     * @param email The email address to check.
     * @param username The username to check.
     * @return true if both the email and username already exist, false otherwise.
     */
    boolean existsByEmailOrUsername(String email, String username);
}