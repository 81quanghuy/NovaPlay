package vn.iotstar.authservice.service;

import vn.iotstar.authservice.model.dto.RoleDTO;
import java.util.List;
import java.util.UUID;

public interface IRoleService {
    /**
     * Retrieves all roles in the system.
     * @return A list of RoleDTOs.
     */
    List<RoleDTO> getAllRoles();

    /**
     * Assigns a permission to a role.
     * @param roleId The ID of the role.
     * @param permissionId The ID of the permission.
     * @return The updated RoleDTO.
     */
    RoleDTO assignPermissionToRole(UUID roleId, Integer permissionId);
}