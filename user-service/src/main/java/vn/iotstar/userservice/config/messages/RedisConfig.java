//package vn.iotstar.userservice.config.messages;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
//import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
//import org.springframework.data.redis.serializer.StringRedisSerializer;
//
//@Configuration
//public class RedisConfig {
//
//    @Bean
//    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
//        RedisTemplate<String, Object> template = new RedisTemplate<>();
//
//        template.setConnectionFactory(connectionFactory);
//
//        template.setKeySerializer(new StringRedisSerializer());
//        template.setHashKeySerializer(new StringRedisSerializer());
//
//        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
//        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
//
//        template.afterPropertiesSet();
//
//        return template;
//    }
//
//    @Bean
//    public RedisConnectionFactory redisConnectionFactory(
//            @Value("${spring.data.redis.host}")
//            String host,
//            @Value("${spring.data.redis.port}")
//            int port,
//            @Value("${spring.data.redis.password:}")
//            String password) {
//
//        var conf = new RedisStandaloneConfiguration(host, port);
//        if (password != null && !password.isBlank()) conf.setPassword(password);
//
//        // Spring Boot 3.x: Lettuce mặc định
//        return new LettuceConnectionFactory(conf);
//    }
//}