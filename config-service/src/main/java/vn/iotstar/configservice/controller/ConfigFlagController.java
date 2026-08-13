package vn.iotstar.configservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.iotstar.configservice.common.GenericResponse;
import vn.iotstar.configservice.model.dto.ConfigFlagDto;
import vn.iotstar.configservice.model.entity.ConfigFlag;
import vn.iotstar.configservice.service.ConfigFlagService;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigFlagController {

    private final ConfigFlagService configFlagService;

    /**
     * Trả thẳng {@link ConfigFlagDto} (KHÔNG bọc {@link GenericResponse}): endpoint này chỉ được
     * gọi service-to-service qua Feign (vd media-service's {@code ConfigServiceClient}), không
     * qua trình duyệt — giữ shape phẳng để Feign deserialize thẳng, đúng tiền lệ đã có ở
     * media-service's {@code MediaController#requestUploadUrl}.
     */
    @GetMapping("/flags/{key}")
    public ConfigFlagDto getFlag(@PathVariable String key) {
        ConfigFlag flag = configFlagService.getByKey(key);
        return new ConfigFlagDto(flag.getKey(), flag.getValue());
    }
}
