package vn.iotstar.emailservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.emailservice.model.entity.Email;

import java.time.Instant;
import java.util.Optional;


@Repository
public interface IEmailRepository extends MongoRepository<Email, String> {

    /**
     * Check if an email exists by its recipient email address, ignoring case.
     *
     * @param recipientEmail the recipient email address
     * @return true if an email with the given recipient email exists, otherwise false
     */
    boolean existsByRecipientEmailIgnoreCase(String recipientEmail);

    /**
     * Find the most recent email by recipient email, OTP, and expiration time greater than the specified time.
     *
     * @param recipientEmail the recipient email address
     * @param otp            the OTP code
     * @param now            the current time to compare with expiration time
     * @return an Optional containing the found Email entity, or empty if not found
     */
    Optional<Email> findTopByRecipientEmailIgnoreCaseAndOtpAndExpirationTimeGreaterThanOrderByCreatedAtDesc(
            String recipientEmail, String otp, Instant now);
}
