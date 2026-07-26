package com.library.api.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for tests that need the real thing.
 *
 * <p>12-Factor X (dev/prod parity): these run against the same PostgreSQL version as
 * production rather than an in-memory substitute. That matters here because the core
 * "one borrower per copy" guarantee relies on a PostgreSQL partial unique index, which a
 * lightweight in-memory database cannot express - so it would test a schema we never ship.
 *
 * <p>This is the <em>singleton container</em> pattern, deliberately not
 * {@code @Testcontainers} + {@code @Container}. That annotation pair stops the container
 * when the first test class finishes, while Spring keeps the application context cached
 * across classes - the second class would then hold a datasource pointing at a container
 * that no longer exists. Starting it once in a static initialiser and letting Ryuk reap it
 * at JVM exit keeps the container and the cached context alive together.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
