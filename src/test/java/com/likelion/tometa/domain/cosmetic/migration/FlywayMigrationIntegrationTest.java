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
        assertEquals(12, successfulMigrationCount(jdbcUrl));
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
        assertEquals(12, successfulMigrationCount(jdbcUrl));
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
        assertEquals(12, successfulMigrationCount(jdbcUrl));
    }

    @Test
    void migrate_backfillsDailyRecordCosmeticSortOrder() throws Exception {
        String jdbcUrl = newJdbcUrl();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                jdbcUrl,
                USERNAME,
                PASSWORD
        );
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/h2/V1__baseline_schema.sql")
        ).execute(dataSource);
        executeUpdate(jdbcUrl, """
                insert into users (user_id, created_at, updated_at)
                values (1, current_timestamp, current_timestamp)
                """);
        executeUpdate(jdbcUrl, """
                insert into cosmetic_products (
                    cosmetic_product_id, created_at, updated_at,
                    source_type, product_type, product_name
                ) values
                    (1, current_timestamp, current_timestamp, 'manual', 'serum', 'first'),
                    (2, current_timestamp, current_timestamp, 'manual', 'serum', 'second')
                """);
        executeUpdate(jdbcUrl, """
                insert into user_cosmetics (
                    user_cosmetic_id, user_id, cosmetic_product_id, created_at, updated_at
                ) values
                    (1, 1, 1, current_timestamp, current_timestamp),
                    (2, 1, 2, current_timestamp, current_timestamp)
                """);
        executeUpdate(jdbcUrl, """
                insert into daily_records (
                    daily_record_id, user_id, record_date, skin_status, created_at, updated_at
                ) values
                    (1, 1, date '2026-08-12', 'normal', current_timestamp, current_timestamp)
                """);
        executeUpdate(jdbcUrl, """
                insert into daily_record_images (
                    daily_record_image_id, daily_record_id, object_key,
                    mime_type, file_size, sort_order, created_at
                ) values (
                    1, 1, 'skin-images/1/attached.jpg',
                    'image/jpeg', 100, 1, current_timestamp
                )
                """);
        executeUpdate(jdbcUrl, """
                insert into daily_record_cosmetics (
                    daily_record_cosmetic_id, daily_record_id, user_cosmetic_id,
                    usage_period, product_type_snapshot, product_name_snapshot, created_at
                ) values
                    (10, 1, 1, 'morning', 'serum', 'first', current_timestamp),
                    (20, 1, 2, 'morning', 'serum', 'second', current_timestamp)
                """);

        Flyway.configure()
                .dataSource(jdbcUrl, USERNAME, PASSWORD)
                .locations(H2_MIGRATION_LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();

        assertEquals(1, queryForInt(jdbcUrl,
                "select sort_order from daily_record_cosmetics "
                        + "where daily_record_cosmetic_id = 10"));
        assertEquals(2, queryForInt(jdbcUrl,
                "select sort_order from daily_record_cosmetics "
                        + "where daily_record_cosmetic_id = 20"));
        assertEquals(1, queryForInt(jdbcUrl,
                "select count(*) from record_image_objects "
                        + "where owner_user_id = 1 "
                        + "and object_key = 'skin-images/1/attached.jpg' "
                        + "and status = 'ATTACHED'"));
    }

    @Test
    void migrate_backfillsDailyRecordSetMembersFromRecordedCosmetics() throws Exception {
        String jdbcUrl = newJdbcUrl();
        Flyway.configure()
                .dataSource(jdbcUrl, USERNAME, PASSWORD)
                .locations(H2_MIGRATION_LOCATION)
                .target("7")
                .load()
                .migrate();

        executeUpdate(jdbcUrl, """
                insert into users (user_id, created_at, updated_at)
                values (1, current_timestamp, current_timestamp)
                """);
        executeUpdate(jdbcUrl, """
                insert into cosmetic_products (
                    cosmetic_product_id, created_at, updated_at,
                    source_type, product_type, product_name
                ) values
                    (1, current_timestamp, current_timestamp, 'manual', 'serum', 'first'),
                    (2, current_timestamp, current_timestamp, 'manual', 'serum', 'second'),
                    (3, current_timestamp, current_timestamp, 'manual', 'serum', 'not-recorded')
                """);
        executeUpdate(jdbcUrl, """
                insert into user_cosmetics (
                    user_cosmetic_id, user_id, cosmetic_product_id, created_at, updated_at
                ) values
                    (1, 1, 1, current_timestamp, current_timestamp),
                    (2, 1, 2, current_timestamp, current_timestamp),
                    (3, 1, 3, current_timestamp, current_timestamp)
                """);
        executeUpdate(jdbcUrl, """
                insert into cosmetic_sets (
                    cosmetic_set_id, user_id, name, usage_time, created_at, updated_at
                ) values (
                    10, 1, 'legacy set', 'MORNING', current_timestamp, current_timestamp
                )
                """);
        executeUpdate(jdbcUrl, """
                alter table cosmetic_set_items
                    drop constraint uk_cosmetic_set_items_set_order
                """);
        executeUpdate(jdbcUrl, """
                insert into cosmetic_set_items (
                    cosmetic_set_item_id, cosmetic_set_id,
                    user_cosmetic_id, item_order, created_at
                ) values
                    (11, 10, 1, 1, current_timestamp),
                    (12, 10, 2, 1, current_timestamp),
                    (13, 10, 3, 3, current_timestamp)
                """);
        executeUpdate(jdbcUrl, """
                insert into daily_records (
                    daily_record_id, user_id, record_date, skin_status, created_at, updated_at
                ) values (
                    20, 1, date '2026-08-12', 'normal', current_timestamp, current_timestamp
                )
                """);
        executeUpdate(jdbcUrl, """
                insert into daily_record_cosmetics (
                    daily_record_cosmetic_id, daily_record_id, user_cosmetic_id,
                    usage_period, product_type_snapshot, product_name_snapshot,
                    sort_order, created_at
                ) values
                    (21, 20, 1, 'morning', 'serum', 'historic first', 1, current_timestamp),
                    (22, 20, 2, 'morning', 'serum', 'historic second', 2, current_timestamp)
                """);
        executeUpdate(jdbcUrl, """
                insert into daily_record_cosmetic_sets (
                    daily_record_cosmetic_set_id, daily_record_id,
                    source_cosmetic_set_id, set_name_snapshot,
                    set_usage_time_snapshot, usage_period, sort_order, created_at
                ) values (
                    30, 20, 10, 'historic set',
                    'morning', 'morning', 1, current_timestamp
                )
                """);

        flyway(jdbcUrl).migrate();

        assertEquals(2, queryForInt(jdbcUrl,
                "select count(*) from daily_record_cosmetic_set_items "
                        + "where daily_record_cosmetic_set_id = 30"));
        assertEquals(1, queryForInt(jdbcUrl,
                "select user_cosmetic_id from daily_record_cosmetic_set_items "
                        + "where daily_record_cosmetic_set_id = 30 and sort_order = 1"));
        assertEquals(2, queryForInt(jdbcUrl,
                "select user_cosmetic_id from daily_record_cosmetic_set_items "
                        + "where daily_record_cosmetic_set_id = 30 and sort_order = 2"));
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
