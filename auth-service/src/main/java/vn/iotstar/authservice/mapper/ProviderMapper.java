package vn.iotstar.authservice.mapper;

import vn.iotstar.authservice.model.dto.ProviderDTO;
import vn.iotstar.authservice.model.entity.Provider;

public class ProviderMapper {

    /**
     * Converts a Provider entity to a ProviderDTO.
     *
     * @param provider the Provider entity to convert
     * @return the converted ProviderDTO, or null if provider is null
     */
    public static ProviderDTO toProviderDTO(Provider provider) {
        if (provider == null) {
            return null;
        }
        return new ProviderDTO(
                provider.getId(),
                provider.getProviderName(),
                provider.getUser() != null ? provider.getUser().getId() : null);
    }
}