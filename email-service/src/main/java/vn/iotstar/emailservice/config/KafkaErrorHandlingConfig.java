//package vn.iotstar.emailservice.config;
//
//import org.apache.kafka.clients.admin.NewTopic;
//import org.apache.kafka.common.TopicPartition;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.kafka.config.TopicBuilder;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
//import org.springframework.kafka.listener.DefaultErrorHandler;
//import org.springframework.util.backoff.FixedBackOff;
//import vn.iotstar.emailservice.util.TopicName;
//
//@Configuration
//public class KafkaErrorHandlingConfig {
//
//    @Bean
//    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<Object, Object> template) {
//        return new DeadLetterPublishingRecoverer(template,
//                (r, e) -> new TopicPartition(r.topic() + ".DLT", r.partition()));
//    }
//
//    @Bean
//    public DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer recoverer) {
//        // retry 3 lần với backoff
//        var backoff = new FixedBackOff(2000L, 3);
//        var handler = new DefaultErrorHandler(recoverer, backoff);
//
//        handler.addNotRetryableExceptions(IllegalArgumentException.class);
//        return handler;
//    }
//
//    @Bean
//    public NewTopic dltTopic() {
//        return TopicBuilder.name(TopicName.SEND_EMAIL +".DLT")
//                .partitions(3).replicas(1).build();
//    }
//
//}
