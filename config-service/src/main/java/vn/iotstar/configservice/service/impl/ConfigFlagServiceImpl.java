package vn.iotstar.configservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.iotstar.configservice.exception.ResourceNotFoundException;
import vn.iotstar.configservice.model.entity.ConfigFlag;
import vn.iotstar.configservice.repository.ConfigFlagRepository;
import vn.iotstar.configservice.service.ConfigFlagService;

@Service
@RequiredArgsConstructor
public class ConfigFlagServiceImpl implements ConfigFlagService {

    private final ConfigFlagRepository configFlagRepository;

    @Override
    public ConfigFlag getByKey(String key) {
        return configFlagRepository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("Config flag not found: " + key));
    }
}
