package com.balancify.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.balancify.backend.service.exception.AccountDeletionException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// A local server stands in for Supabase Auth's admin API.
class SupabaseAuthAdminClientUserEmailTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private HttpServer server;
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<String> body = new AtomicReference<>("{}");
    private final AtomicReference<String> seenAuthorization = new AtomicReference<>();
    private SupabaseAuthAdminClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/v1/admin/users/", exchange -> {
            seenAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        SupabaseAuthProperties properties = new SupabaseAuthProperties();
        properties.setSupabaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setServiceRoleKey("YOUR_API_KEY");
        properties.setVerifyTimeoutMs(2000);
        client = new SupabaseAuthAdminClient(properties, HttpClient.newHttpClient());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void readsTheEmailOfTheLoginAccount() {
        body.set("{\"id\":\"" + USER_ID + "\",\"email\":\" YOUR_USERNAME@Example.com \"}");

        assertThat(client.findUserEmail(USER_ID)).contains("your_username@example.com");
        assertThat(seenAuthorization.get()).isEqualTo("Bearer YOUR_API_KEY");
    }

    @Test
    void findsNothingForAGoneOrEmaillessAccount() {
        status.set(404);
        body.set("");
        assertThat(client.findUserEmail(USER_ID)).isEmpty();

        status.set(200);
        body.set("{\"id\":\"" + USER_ID + "\",\"email\":\"\"}");
        assertThat(client.findUserEmail(USER_ID)).isEmpty();
    }

    @Test
    void reportsTheServiceUnavailableOnOtherAnswers() {
        status.set(500);
        body.set("{}");

        assertThatThrownBy(() -> client.findUserEmail(USER_ID))
            .isInstanceOf(AccountDeletionException.class)
            .hasFieldOrPropertyWithValue("reason", AccountDeletionException.Reason.AUTH_UNAVAILABLE);
    }
}
