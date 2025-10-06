package vn.iotstar.emailservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.emailservice.model.entity.Email;

import java.util.Optional;


@Repository
public interface IEmailRepository extends MongoRepository<Email, String> {
    /**
     * Find an email by its recipient email address.
     *
     * @param email the recipient email address
     * @return the Email entity if found, otherwise null
     */
    Optional<Email> findTopByRecipientEmailOrderByCreatedAtDesc(String email);
}
