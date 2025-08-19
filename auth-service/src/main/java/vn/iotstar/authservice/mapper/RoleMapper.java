package vn.iotstar.authservice.mapper;

import vn.iotstar.authservice.model.dto.RoleDTO;
import vn.iotstar.authservice.model.entity.Role;
import java.util.stream.Collectors;

public class RoleMapper {

    /**
     * Converts a Role entity to a RoleDTO.
     * @param role the Role entity to convert
     * @return the converted RoleDTO, or null if role is null
     */
    public static RoleDTO toRoleDTO(Role role) {
        if (role == null) {
            return null;
        }
        return new RoleDTO(
                role.getId(),
                role.getRoleName(),
                role.getDescription(),
                role.getPermissions().stream()
                        .map(PermissionMapper::toPermissionDTO)
                        .collect(Collectors.toSet())
        );
    }

    /**
     * Converts a RoleDTO to a Role entity.
     * @param roleDTO the RoleDTO to convert
     * @return the converted Role entity, or null if roleDTO is null
     */
    public static Role toRole(RoleDTO roleDTO) {
        if (roleDTO == null) {
            return null;
        }
        Role role = new Role();
        role.setId(roleDTO.id());
        role.setRoleName(roleDTO.roleName());
        role.setDescription(roleDTO.description());
        return role;
    }
}