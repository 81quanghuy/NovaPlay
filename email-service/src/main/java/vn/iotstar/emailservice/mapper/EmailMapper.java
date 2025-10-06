package vn.iotstar.emailservice.mapper;

import vn.iotstar.emailservice.model.dto.EmailDTO;
import vn.iotstar.emailservice.model.entity.Email;

import java.time.Instant;

/**
 * Mapper class for Email-related operations.
 * This class will contain methods to map between Email entities and DTOs.
 */
public class EmailMapper {

    /**
     * Converts an EmailDTO to an Email entity.
     *
     * @param emailDTO the EmailDTO to convert
     * @return the converted Email entity, or null if emailDTO is null
     */
    public static Email toEntity(EmailDTO emailDTO) {
        if (emailDTO == null) {
            return null;
        }
        Email email = new Email();
        email.setRecipientEmail(emailDTO.email());
        email.setOtp(emailDTO.otp());
        email.setExpirationTime(Instant.parse(emailDTO.expirationTime()));
        return email;
    }

    /**
     * Converts an Email entity to an EmailDTO.
     *
     * @param email the Email entity to convert
     * @return the converted EmailDTO, or null if email is null
     */
    public static EmailDTO toDTO(Email email) {
        if (email == null) {
            return null;
        }
        return new EmailDTO(
                email.getRecipientEmail(),
                email.getOtp(),
                email.getExpirationTime().toString());
    }
}
