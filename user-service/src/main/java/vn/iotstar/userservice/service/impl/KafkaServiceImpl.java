package vn.iotstar.userservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import vn.iotstar.userservice.service.UserProfileService;
import vn.iotstar.userservice.util.TopicName;
import vn.iotstar.utils.dto.MediaReadyEvent;
import vn.iotstar.utils.dto.UserRegister;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaServiceImpl {

    private final UserProfileService userProfileService;

    @KafkaListener(topics= TopicName.ACTIVATE_ACCOUNT,
            containerFactory = "kafkaListenerContainerFactory")
    public void handle(UserRegister evt) {
        validateUserRegister(evt);
        try {
            userProfileService.registerNewUser(evt);
        } catch (Exception ex) {
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }
    private void validateUserRegister(UserRegister evt) {
        if (evt == null) throw new IllegalArgumentException("Null payload");
        if (evt.username() == null || evt.username().isBlank())
            throw new IllegalArgumentException("Invalid username");
        if (evt.email() == null || evt.email().isBlank())
            throw new IllegalArgumentException("Invalid email");
    }

    @KafkaListener(topics= TopicName.SEND_STATUS_MEDIA,
            containerFactory = "kafkaListenerContainerFactory")
    public void handleMediaReadyEvent(MediaReadyEvent event) {
        try {
            userProfileService.changeUrlAvatar(event.cdnUrl(), event.ownerId());
        } catch (Exception ex) {
            throw (ex instanceof RuntimeException) ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }
}

