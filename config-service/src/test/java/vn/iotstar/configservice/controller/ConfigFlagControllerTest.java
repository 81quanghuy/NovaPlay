package vn.iotstar.configservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.iotstar.configservice.exception.ResourceNotFoundException;
import vn.iotstar.configservice.model.entity.ConfigFlag;
import vn.iotstar.configservice.service.ConfigFlagService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigFlagController.class)
class ConfigFlagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConfigFlagService configFlagService;

    @Test
    @DisplayName("GET /api/v1/config/flags/{key} trả 200 kèm value khi key tồn tại")
    void returns200WhenKeyExists() throws Exception {
        when(configFlagService.getByKey("media-storage-provider"))
                .thenReturn(new ConfigFlag("media-storage-provider", "cloudflare-r2"));

        mockMvc.perform(get("/api/v1/config/flags/media-storage-provider"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("media-storage-provider"))
                .andExpect(jsonPath("$.value").value("cloudflare-r2"));
    }

    @Test
    @DisplayName("GET /api/v1/config/flags/{key} trả 404 khi key chưa được cấu hình")
    void returns404WhenKeyMissing() throws Exception {
        when(configFlagService.getByKey("unknown-key"))
                .thenThrow(new ResourceNotFoundException("Config flag not found: unknown-key"));

        mockMvc.perform(get("/api/v1/config/flags/unknown-key"))
                .andExpect(status().isNotFound());
    }
}
