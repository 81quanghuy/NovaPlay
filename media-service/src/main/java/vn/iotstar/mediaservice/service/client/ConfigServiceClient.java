package vn.iotstar.mediaservice.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.iotstar.mediaservice.service.client.dto.ConfigFlagDto;

/**
 * Gọi config-service để đọc giá trị cờ (feature flag) hiện tại — nguồn cờ thật sự đứng sau
 * {@code configServiceFeatureProvider} trong {@code OpenFeatureConfig}. Sửa giá trị bằng cách sửa
 * document trong MongoDB của config-service (mongo-express/mongosh), không có endpoint ghi ở đây.
 */
@FeignClient(name = "config-service", url = "${services.config}", contextId = "ConfigServiceClient", path = "/api/v1/config")
public interface ConfigServiceClient {

    @GetMapping("/flags/{key}")
    ConfigFlagDto getFlag(@PathVariable("key") String key);
}
