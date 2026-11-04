package fr.isep.simrecrut.mapper.identity;

import fr.isep.simrecrut.entity.identity.*;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdentityAdminMapper {
    UserAccount findPublic(@Param("publicId") String publicId);
    UserAccount lockUser(@Param("publicId") String publicId);
    List<UserAccount> listUsers(@Param("keyword") String keyword, @Param("status") AccountStatus status,
            @Param("role") AccountRole role, @Param("offset") int offset, @Param("size") int size);
    long countUsers(@Param("keyword") String keyword, @Param("status") AccountStatus status,
            @Param("role") AccountRole role);
    List<Role> lockRoles();
    long countAvailableAdmins();
    int updateEmail(@Param("id") long id, @Param("email") String email, @Param("revision") long revision);
    int updateStatus(@Param("id") long id, @Param("status") AccountStatus status,
            @Param("revision") long revision);
    int touchRoles(@Param("id") long id, @Param("revision") long revision);
    int deleteUserRoles(@Param("userId") long userId);
    List<PermissionRow> permissions();
    List<String> rolePermissions(@Param("roleId") long roleId);
    int deleteRolePermissions(@Param("roleId") long roleId);
    int insertRolePermission(@Param("roleId") long roleId, @Param("permissionId") long permissionId);
    int touchRole(@Param("roleId") long roleId, @Param("revision") long revision);
    int invalidateRoleSessions(@Param("roleId") long roleId);
    int resetPassword(@Param("id") long id, @Param("email") String email,
            @Param("securityVersion") long securityVersion, @Param("passwordHash") String passwordHash);
    int audit(@Param("actorId") Long actorId, @Param("action") String action,
            @Param("resourceType") String resourceType, @Param("resourceHash") String resourceHash);

    record PermissionRow(long id, String code, String name, String description) { }
}
