package com.englishhero.identity;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface IdentityMapper {
    @Select("SELECT id,username,display_name,password_hash,enabled,security_version FROM identity_user WHERE username=#{username}")
    IdentityUser byUsername(String username);
    @Select("SELECT id,username,display_name,password_hash,enabled,security_version FROM identity_user WHERE id=#{id}")
    IdentityUser byId(long id);
    @Select("SELECT role FROM identity_user_role WHERE user_id=#{id} ORDER BY role") List<String> roles(long id);
    @Insert("INSERT INTO identity_user(username,display_name,password_hash) VALUES(#{username},#{displayName},#{hash})")
    void insert(@Param("username") String username, @Param("displayName") String displayName, @Param("hash") String hash);
    @Insert("INSERT INTO identity_user_role(user_id,role) VALUES(#{id},#{role})")
    void role(@Param("id") long id, @Param("role") String role);
    @Update("UPDATE identity_user SET display_name=#{name},updated_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND enabled=1 AND security_version=#{version}")
    int profile(@Param("id") long id, @Param("version") int version, @Param("name") String name);
    @Update("UPDATE identity_user SET password_hash=#{hash},security_version=security_version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND enabled=1 AND security_version=#{version}")
    int password(@Param("id") long id, @Param("version") int version, @Param("hash") String hash);
}
