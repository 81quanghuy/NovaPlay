package vn.iotstar.userservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.iotstar.userservice.mapper.UserProfileMapper;
import vn.iotstar.userservice.model.dto.UserProfileDTO;
import vn.iotstar.userservice.model.entity.UserProfile;
import vn.iotstar.userservice.repository.IUserProfileRepository;

import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class KeycloakEventConfig {

    private final IUserProfileRepository userProfileRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Consumer for Keycloak events from Kafka.
     * This method listens for messages on the 'keycloak-events' topic and processes them.
     * It handles 'REGISTER' events to create new user profiles in the system.
     *
     * @return a Consumer that processes Keycloak event messages
     */
    @Bean
    public Consumer<String> keycloakEventConsumer() {
        return message -> {
            // Đây là một lambda function, message là payload từ Kafka
            log.info("Received a new event via Spring Cloud Stream from Kafka: {}", message);

            try {
                JsonNode event = objectMapper.readTree(message);
                String eventType = event.path("type").asText();

                if ("REGISTER".equals(eventType)) {
                    UUID userId = UUID.fromString(event.path("userId").asText());
                    JsonNode details = event.path("details");
                    String username = details.path("username").asText();
                    String email = details.path("email").asText();

                    log.info("Processing 'REGISTER' event for userId: [{}], username: [{}]", userId, username);

                    if (userProfileRepository.existsById(userId)) {
                        log.warn("Profile for userId [{}] already exists. Skipping creation.", userId);
                        return;
                    }
                    UserProfile newProfile = UserProfileMapper.toUserProfile(
                            new UserProfileDTO(userId, username, email)
                    );
                    userProfileRepository.save(newProfile);

                    log.info("Successfully created a new profile for userId: [{}]", userId);
                } else {
                    log.debug("Event type is '{}'. Ignoring.", eventType);
                }

            } catch (Exception e) {
                log.error("Failed to process message from Kafka. Message: {}", message, e);
            }
        };
    }
}