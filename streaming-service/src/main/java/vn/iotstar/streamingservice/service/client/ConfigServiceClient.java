package vn.iotstar.streamingservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.iotstar.streamingservice.service.client.dto.ConfigFlagDto;

/**
 * Gọi config-service để đọc giá trị cờ (feature flag) hiện tại — nguồn cờ thật sự đứng sau
 * {@code configServiceFeatureProvider} trong {@code OpenFeatureConfig}. Sửa giá trị bằng cách sửa
 * document trong MongoDB của config-service, không có endpoint ghi ở đây.
 * <p>
 * CỐ Ý không gắn {@code configuration = FeignSecurityConfig.class}: đây là lời gọi hạ tầng đọc cờ,
 * không gắn với danh tính người xem nào — bản sao của {@code ConfigServiceClient} bên media-service.
 */
@FeignClient(name = "config-service", url = "${services.config}", contextId = "ConfigServiceClient", path = "/api/v1/config")
public interface ConfigServiceClient {

    @GetMapping("/flags/{key}")
    ConfigFlagDto getFlag(@PathVariable("key") String key);
}
