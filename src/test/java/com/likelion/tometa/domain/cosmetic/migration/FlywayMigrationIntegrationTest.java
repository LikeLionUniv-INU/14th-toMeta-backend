package com.likelion.tometa.domain.cosmetic.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlywayMigrationIntegrationTest {

    private static final String USERNAME = "sa";
    private static final String PASSWORD = "test";
    private static final String H2_MIGRATION_LOCATION = "classpath:db/migration/h2";

    @Test
    void migrate_isIdempotent() throws Exception {
        String jdbcUrl = newJdbcUrl();
        Flyway flyway = flyway(jdbcUrl);

        flyway.migrate();
        flyway.migrate();

        assertEquals(100, count(jdbcUrl, "ingredients"));
        assertEquals(100, countDistinctIngredientNames(jdbcUrl));
        assertEquals(3, successfulMigrationCount(jdbcUrl));
    }

    @Test
    @Timeout(10)
    void concurrentMigrate_isSerializedWithoutDuplicateIngredients() throws Exception {
        String jdbcUrl = newJdbcUrl();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> migrateAfterSignal(jdbcUrl, ready, start));
            Future<?> second = executor.submit(() -> migrateAfterSignal(jdbcUrl, ready, start));

            ready.await();
            start.countDown();
            first.get();
            second.get();
        }

        assertEquals(100, count(jdbcUrl, "ingredients"));
        assertEquals(100, countDistinctIngredientNames(jdbcUrl));
        assertEquals(3, successfulMigrationCount(jdbcUrl));
    }

    @Test
    void migrate_baselinesExistingSchemaAndSeedsOnlyMissingIngredients() throws Exception {
        String jdbcUrl = newJdbcUrl();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                jdbcUrl,
                USERNAME,
                PASSWORD
        );
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/h2/V1__baseline_schema.sql")
        ).execute(dataSource);
        executeUpdate(
                jdbcUrl,
                "insert into ingredients (name, created_at) values "
                        + "('판테놀', timestamp '2025-01-02 03:04:05')"
        );

        Flyway.configure()
                .dataSource(jdbcUrl, USERNAME, PASSWORD)
                .locations(H2_MIGRATION_LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();

        assertEquals(100, count(jdbcUrl, "ingredients"));
        assertEquals(100, countDistinctIngredientNames(jdbcUrl));
        assertEquals(
                1,
                queryForInt(
                        jdbcUrl,
                        "select count(*) from ingredients "
                                + "where name = '판테놀' "
                                + "and created_at = timestamp '2025-01-02 03:04:05'"
                )
        );
        assertEquals(3, successfulMigrationCount(jdbcUrl));
    }

    private void migrateAfterSignal(
            String jdbcUrl,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        try {
            ready.countDown();
            start.await();
            flyway(jdbcUrl).migrate();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Flyway 동시 실행 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }

    private Flyway flyway(String jdbcUrl) {
        return Flyway.configure()
                .dataSource(jdbcUrl, USERNAME, PASSWORD)
                .locations(H2_MIGRATION_LOCATION)
                .load();
    }

    private String newJdbcUrl() {
        return "jdbc:h2:mem:flyway-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    private int count(String jdbcUrl, String tableName) throws SQLException {
        return queryForInt(jdbcUrl, "select count(*) from " + tableName);
    }

    private int countDistinctIngredientNames(String jdbcUrl) throws SQLException {
        return queryForInt(jdbcUrl, "select count(distinct name) from ingredients");
    }

    private int successfulMigrationCount(String jdbcUrl) throws SQLException {
        return queryForInt(
                jdbcUrl,
                "select count(*) from \"flyway_schema_history\" "
                        + "where \"success\" = true and \"version\" is not null"
        );
    }

    private int queryForInt(String jdbcUrl, String sql) throws SQLException {
        try (
                Connection connection = DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD);
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private void executeUpdate(String jdbcUrl, String sql) throws SQLException {
        try (
                Connection connection = DriverManager.getConnection(jdbcUrl, USERNAME, PASSWORD);
                Statement statement = connection.createStatement()
        ) {
            statement.executeUpdate(sql);
        }
    }
}
