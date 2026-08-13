package vn.iotstar.configservice.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.iotstar.configservice.exception.ResourceNotFoundException;
import vn.iotstar.configservice.model.entity.ConfigFlag;
import vn.iotstar.configservice.repository.ConfigFlagRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigFlagServiceImplTest {

    @Mock
    private ConfigFlagRepository configFlagRepository;

    private ConfigFlagServiceImpl service;

    @Test
    @DisplayName("trả về ConfigFlag khi key tồn tại")
    void returnsFlagWhenKeyExists() {
        service = new ConfigFlagServiceImpl(configFlagRepository);
        ConfigFlag flag = new ConfigFlag("media-storage-provider", "aws-s3");
        when(configFlagRepository.findById("media-storage-provider")).thenReturn(Optional.of(flag));

        ConfigFlag result = service.getByKey("media-storage-provider");

        assertThat(result.getValue()).isEqualTo("aws-s3");
    }

    @Test
    @DisplayName("ném ResourceNotFoundException khi key chưa được cấu hình")
    void throwsWhenKeyMissing() {
        service = new ConfigFlagServiceImpl(configFlagRepository);
        when(configFlagRepository.findById("unknown-key")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByKey("unknown-key"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
