package com.example.ordermanagement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
        DockerImageName.parse("postgres:16")
    )
        .withDatabaseName("order_management")
        .withUsername("postgres")
        .withPassword("postgres")
        .withEnv("OMS_OWNER_PASSWORD", "owner-password")
        .withEnv("OMS_APP_PASSWORD", "app-password")
        .withCopyFileToContainer(
            MountableFile.forHostPath("docker/local/postgres/init/01-roles.sh"),
            "/docker-entrypoint-initdb.d/01-roles.sh"
        );

    @Test
    void migrationsCreateTheSchema() throws Exception {
        Flyway flyway = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), "oms_owner", "owner-password")
            .locations("classpath:db/migration")
            .load();
        flyway.migrate();

        try (
            Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                "oms_app",
                "app-password"
            );
            Statement statement = connection.createStatement()
        ) {
            assertThat(
                count(statement, "select count(*) from \"order\"")
            ).isZero();
            assertThat(
                count(statement, "select count(*) from order_item")
            ).isZero();
            assertThat(
                count(
                    statement,
                    "select count(*) from information_schema.columns " +
                        "where table_name = 'order_item' and column_name in " +
                        "('product_id', 'product_name', 'price_unit', 'unit_price', 'quantity')"
                )
            ).isEqualTo(5);
        }
    }

    private long count(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
