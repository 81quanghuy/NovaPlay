package vn.iotstar.promotionservice.service;

public interface EventPublisher {

    void publish(String topic, String key, Object payload);
}
