package vn.iotstar.emailservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.emailservice.model.entity.LogMQ;

@Repository
public interface IEmailMessageLogRepository extends MongoRepository<LogMQ, String> {

    /**
     * Find a LogMQ by its messageId.
     *
     * @param recipient email recipient
     * @return the LogMQ with the specified messageId, or null if not found
     */
    LogMQ findByRecipient(String recipient);
}
