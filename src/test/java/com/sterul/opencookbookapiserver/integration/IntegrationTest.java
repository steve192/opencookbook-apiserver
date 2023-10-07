package com.sterul.opencookbookapiserver.integration;

import org.junit.ClassRule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

public class IntegrationTest {
    
    @ClassRule
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("postgres:16-alpine");

    @BeforeAll
    public static void startup() {
        postgreSQLContainer.start();
    }

    @AfterAll
    public static void stop() {
        postgreSQLContainer.stop();
    }

}
