package com.example.aigateway.mapper;

import com.example.aigateway.entity.UserAccount;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies user mapper integration behavior. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class UserMapperIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ai_gateway")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

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
