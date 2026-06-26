package com.example.aigateway.mapper;

import com.example.aigateway.entity.UserAccount;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/ai_gateway?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
        "spring.datasource.username=root",
        "spring.datasource.password=root",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver"
})
class UserMapperIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    @Sql(scripts = "file:sql/schema.sql")
    void insertUserShouldGenerateIdAndBeQueryable() {
        String username = "mapper_user_" + UUID.randomUUID().toString().replace("-", "");
        UserAccount user = new UserAccount(username, username + "@example.com", "hashed_password");

        userMapper.insertUser(user);

        assertNotNull(user.getId());
        assertTrue(user.getId() > 0);

        UserAccount savedUser = userMapper.getUserById(user.getId());

        assertEquals(username, savedUser.getUsername());
        assertEquals(username + "@example.com", savedUser.getEmail());
        assertEquals("hashed_password", savedUser.getPasswordHash());
        assertEquals(Boolean.TRUE, savedUser.getEnabled());
    }
}
