package vn.iotstar.authservice.config.messages;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import vn.iotstar.authservice.util.TopicName;


@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic userRegisteredTopic() {
        return TopicBuilder.name(TopicName.USER_REGISTERED)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }

    @Bean
    public NewTopic forgotPasswordTopic() {
        return TopicBuilder.name(TopicName.FORGOT_PASSWORD)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .build();
    }
}
