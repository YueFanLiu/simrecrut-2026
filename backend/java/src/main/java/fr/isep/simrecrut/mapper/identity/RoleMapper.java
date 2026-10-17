package fr.isep.simrecrut.mapper.identity;

import fr.isep.simrecrut.entity.identity.AccountRole;
import fr.isep.simrecrut.entity.identity.Role;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMapper {

    Role findByCode(@Param("roleCode") AccountRole roleCode);

    List<Role> findByUserId(@Param("userId") long userId);

    int insertUserRole(
        @Param("userId") long userId,
        @Param("roleId") long roleId);
}
