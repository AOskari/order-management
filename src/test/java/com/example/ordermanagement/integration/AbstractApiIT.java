package com.example.ordermanagement.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractApiIT {

    private static final boolean DOCKER_AVAILABLE = isDockerAvailable();

    /*
     * These are JVM-wide singleton containers, deliberately not annotated with @Container.
     * The Testcontainers extension stops @Container fields after each test class, while
     * Spring's context cache keeps the old JDBC URL for later classes. Starting them once
     * per JVM keeps the context and the containers aligned. They are stopped by a
     * shutdown hook (and by Ryuk).
     */
    static final PostgreSQLContainer POSTGRES = DOCKER_AVAILABLE
        ? createPostgres()
        : null;
    static final GenericContainer<?> KEYCLOAK = DOCKER_AVAILABLE
        ? createKeycloak()
        : null;

    static {
        if (DOCKER_AVAILABLE) {
            POSTGRES.start();
            KEYCLOAK.start();
            Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {
                    stopQuietly(KEYCLOAK);
                    stopQuietly(POSTGRES);
                })
            );
        }
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Value("${local.server.port}")
    protected int port;

    protected final RestTemplate rest = createRestTemplate();

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("delete from \"order\"");
    }

    protected String apiUrl(String path) {
        return "http://localhost:" + port + path;
    }

    protected Map<String, Object> createOrder(String username) {
        UUID orderId = UUID.randomUUID();
        UUID customerId = customerId(username);
        jdbc.update(
            "insert into \"order\" (id, customer_id, status, total_amount) values (?, ?, 'CREATED', 119.97)",
            orderId,
            customerId
        );
        return Map.of(
            "id",
            orderId.toString(),
            "customerId",
            customerId.toString()
        );
    }

    protected UUID customerId(String username) {
        String payload = token(username).split("\\.")[1];
        String claims = new String(
            Base64.getUrlDecoder().decode(payload),
            StandardCharsets.UTF_8
        );
        int claim = claims.indexOf("\"sub\"");
        int colon = claims.indexOf(':', claim);
        int start = claims.indexOf('"', colon) + 1;
        int end = claims.indexOf('"', start);
        if (claim < 0 || colon < 0 || start == 0 || end < start) {
            throw new IllegalStateException(
                "Unable to read the subject from the access token"
            );
        }
        return UUID.fromString(claims.substring(start, end));
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (!DOCKER_AVAILABLE) {
            return;
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "oms_app");
        registry.add("spring.datasource.password", () -> "app-password");

        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> "oms_owner");
        registry.add("spring.flyway.password", () -> "owner-password");

        registry.add(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            () -> keycloakUrl() + "/realms/order-management"
        );
    }

    protected String token(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "order-management-api");
        form.add("username", username);
        form.add("password", "password");
        form.add("scope", "openid");

        @SuppressWarnings("rawtypes")
        Map response = new RestTemplate().postForObject(
            keycloakUrl() +
                "/realms/order-management/protocol/openid-connect/token",
            new HttpEntity<>(form, headers),
            Map.class
        );
        return (String) response.get("access_token");
    }

    protected HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static PostgreSQLContainer createPostgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
            .withDatabaseName("order_management")
            .withUsername("postgres")
            .withPassword("postgres")
            .withEnv("OMS_OWNER_PASSWORD", "owner-password")
            .withEnv("OMS_APP_PASSWORD", "app-password")
            .withCopyFileToContainer(
                MountableFile.forHostPath(
                    "docker/local/postgres/init/01-roles.sh"
                ),
                "/docker-entrypoint-initdb.d/01-roles.sh"
            );
    }

    private static GenericContainer<?> createKeycloak() {
        return new GenericContainer<>(
            DockerImageName.parse("quay.io/keycloak/keycloak:26.0")
        )
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(
                MountableFile.forHostPath(
                    "docker/local/keycloak/realm-export.json"
                ),
                "/opt/keycloak/data/import/realm-export.json"
            )
            .withExposedPorts(8080)
            .withStartupTimeout(Duration.ofMinutes(2))
            .waitingFor(
                Wait.forHttp("/realms/order-management")
                    .forPort(8080)
                    .forStatusCode(200)
            );
    }

    private static RestTemplate createRestTemplate() {
        RestTemplate restTemplate = new RestTemplate(
            new JdkClientHttpRequestFactory()
        );
        restTemplate.setErrorHandler(
            new ResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response) {
                    return false;
                }

                @Override
                public void handleError(
                    URI url,
                    HttpMethod method,
                    ClientHttpResponse response
                ) {
                    // Tests assert status codes directly; do not throw on 4xx/5xx.
                }
            }
        );
        return restTemplate;
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static void stopQuietly(AutoCloseable container) {
        try {
            container.close();
        } catch (Exception ignored) {
            // Best-effort cleanup; Ryuk is the fallback.
        }
    }

    private static String keycloakUrl() {
        return "http://localhost:" + KEYCLOAK.getMappedPort(8080);
    }
}
