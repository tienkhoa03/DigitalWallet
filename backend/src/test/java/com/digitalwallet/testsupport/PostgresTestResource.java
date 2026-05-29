package com.digitalwallet.testsupport;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code @QuarkusTestResource}-style Postgres 16 Testcontainer.
 *
 * <p>Each test class that touches the DB declares
 * {@code @QuarkusTestResource(PostgresTestResource.class)} — a container starts before the
 * Quarkus test app boots, Flyway runs V1 against it, the container shuts down afterwards.
 *
 * <p>H2 / embedded Postgres are forbidden by {@code .claude/rules/testing.md §2.4}.
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:16-alpine");

    private PostgreSQLContainer<?> container;

    @Override
    public Map<String, String> start() {
        container = new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("digitalwallet_test")
                .withUsername("test")
                .withPassword("test");
        container.start();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", container.getJdbcUrl());
        config.put("quarkus.datasource.username", container.getUsername());
        config.put("quarkus.datasource.password", container.getPassword());
        return config;
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
