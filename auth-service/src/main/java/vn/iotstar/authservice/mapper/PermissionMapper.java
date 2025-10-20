package vn.iotstar.authservice.mapper;

import vn.iotstar.authservice.model.dto.PermissionDTO;
import vn.iotstar.authservice.model.entity.Permission;

public class PermissionMapper {

    /**
     * Converts a Permission entity to a PermissionDTO.
     *
     * @param permission the Permission entity to convert
     * @return the converted PermissionDTO, or null if permission is null
     */
    public static PermissionDTO toPermissionDTO(Permission permission) {
        if (permission == null) {
            return null;
        }
        return new PermissionDTO(
                permission.getId(),
                permission.getName(),
                permission.getDescription()
        );
    }

    /**
     * Converts a PermissionDTO to a Permission entity.
     *
     * @param permissionDTO the PermissionDTO to convert
     * @return the converted Permission entity, or null if permissionDTO is null
     */
    public static Permission toPermission(PermissionDTO permissionDTO) {
        if (permissionDTO == null) {
            return null;
        }
        Permission permission = new Permission();
        permission.setId(permissionDTO.id());
        permission.setName(permissionDTO.name());
        permission.setDescription(permissionDTO.description());
        return permission;
    }
}