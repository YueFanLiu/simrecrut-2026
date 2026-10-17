package fr.isep.simrecrut.mapper.identity;

import fr.isep.simrecrut.entity.identity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAccountMapper {

    UserAccount findById(@Param("id") long id);

    UserAccount findByUsername(@Param("username") String username);

    UserAccount findByEmail(@Param("email") String email);

    int insert(UserAccount account);
}
