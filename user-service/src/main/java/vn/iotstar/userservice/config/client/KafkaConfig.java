package vn.iotstar.userservice.config.client;//package vn.iotstar.authservice.config.client;
//
//import org.apache.kafka.clients.admin.NewTopic;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.kafka.annotation.EnableKafka;
//import org.springframework.kafka.core.KafkaOperations;
//import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
//import org.springframework.kafka.listener.DefaultErrorHandler;
//import org.springframework.util.backoff.FixedBackOff;
//import vn.iostar.constant.KafkaTopicName;
//
//@EnableKafka
//@Configuration
//public class KafkaConfig {
//    @Bean
//    public DefaultErrorHandler errorHandler(KafkaOperations<String, Object> template) {
//        return new DefaultErrorHandler(
//                new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 2L));
//    }
//    @Bean
//    public NewTopic email_register() {
//        return new NewTopic(KafkaTopicName.EMAIL_REGISTER_TOPIC, 2, (short) 1);
//    }
//
//    @Bean
//    public NewTopic getUserInf() {
//        return new NewTopic(KafkaTopicName.EMAIL_FORGOT_PASSWORD_TOPIC, 2, (short) 1);
//    }
//
//    @Bean
//    public NewTopic verifyParent() {
//        return new NewTopic(KafkaTopicName.EMAIL_VERIFY_PARENT_TOPIC, 2, (short) 1);
//    }
//}
