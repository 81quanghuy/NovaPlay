package vn.iotstar.authservice.service;

public interface EventPublisher {

    void publish(String topic, String key, Object payload);
}
