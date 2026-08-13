package vn.iotstar.configservice.service;

import vn.iotstar.configservice.model.entity.ConfigFlag;

public interface ConfigFlagService {

    /**
     * @throws vn.iotstar.configservice.exception.ResourceNotFoundException nếu key chưa được cấu
     *         hình — phía gọi (một OpenFeature {@code FeatureProvider}) coi đây là "chưa có giá
     *         trị", không phải lỗi hệ thống, và tự fallback về default của chính nó.
     */
    ConfigFlag getByKey(String key);
}
