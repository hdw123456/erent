package com.example.aigateway.mapper;

import com.example.aigateway.entity.UserApiKey;
import com.example.aigateway.entity.UserAccount;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** MyBatis persistence operations for user data. */
public interface UserMapper {
    // Define methods for mapping UserAccount objects to database operations
    void insertUser(UserAccount user);
    UserAccount getUserById(@Param("id") long id);
    UserAccount getUserByUsername(@Param("username") String username);
    UserApiKey getUserApiKeyByUserId(@Param("userId") long userId);
    List<UserAccount> getAllUsers();
    List<UserApiKey> getAllUserApiKeys();
    void updateUser(UserAccount user);
    void deleteUser(@Param("id") long id);
    List<String> getRole(@Param("id") long id);
    void insertUserRole(@Param("userId") long userId, @Param("roleCode") String roleCode);
    
}
