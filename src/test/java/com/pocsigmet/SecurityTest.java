package com.pocsigmet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Testes de segurança — path traversal e headers HTTP.
 * Executar com: mvn test -Dsecurity.tests=true
 * Requerem a aplicação rodando em localhost:8082.
 */
@EnabledIfSystemProperty(named = "security.tests", matches = "true")
class SecurityTest {

    private static final String BASE = "http://localhost:8082";
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
            HttpRequest.newBuilder().uri(URI.create(BASE + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    // ── Path Traversal ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /data/../etc/passwd → 400")
    void dataPathTraversal() throws Exception {
        int status = get("/data/..%2Fetc%2Fpasswd").statusCode();
        assertTrue(status == 400 || status == 404, "esperado 400 ou 404, recebido: " + status);
    }

    @Test
    @DisplayName("GET /frame/../etc/passwd → 400")
    void framePathTraversal() throws Exception {
        int status = get("/frame/..%2Fetc%2Fpasswd").statusCode();
        assertTrue(status == 400 || status == 404, "esperado 400 ou 404, recebido: " + status);
    }

    @Test
    @DisplayName("GET /canal16frame/../etc/passwd → 400")
    void canal16PathTraversal() throws Exception {
        int status = get("/canal16frame/..%2Fetc%2Fpasswd").statusCode();
        assertTrue(status == 400 || status == 404, "esperado 400 ou 404, recebido: " + status);
    }

    @Test
    @DisplayName("POST /convection/process?imagePath=../../etc/passwd → 400")
    void convectionPathTraversal() throws Exception {
        HttpResponse<String> resp = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/convection/process?imagePath=../../etc/passwd"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(400, resp.statusCode());
    }

    // ── Funcionalidade normal continua ok ────────────────────────────────────

    @Test
    @DisplayName("GET /frames → 200 (funcionalidade normal intacta)")
    void framesEndpointOk() throws Exception {
        int status = get("/frames").statusCode();
        assertEquals(200, status);
    }

    @Test
    @DisplayName("GET /canal16frames → 200 (funcionalidade normal intacta)")
    void canal16framesEndpointOk() throws Exception {
        int status = get("/canal16frames").statusCode();
        assertEquals(200, status);
    }

    // ── Headers de segurança ─────────────────────────────────────────────────

    @Test
    @DisplayName("Headers de segurança presentes na resposta")
    void securityHeaders() throws Exception {
        HttpResponse<String> resp = get("/health");
        assertEquals("DENY", resp.headers().firstValue("X-Frame-Options").orElse(""));
        assertEquals("nosniff", resp.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertEquals("1; mode=block", resp.headers().firstValue("X-XSS-Protection").orElse(""));
    }
}
