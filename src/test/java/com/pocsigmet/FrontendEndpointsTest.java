package com.pocsigmet;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa todos os endpoints consumidos pelo front-end.
 * Roda contra a aplicação em execução (localhost:80 via nginx).
 * Propósito: garantir contrato antes e depois da migração para Spring MVC.
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
public class FrontendEndpointsTest {

    private static final String BASE = System.getProperty("test.base.url", "http://localhost");
    private static String sessionCookie = "";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @BeforeAll
    static void login() throws Exception {
        // 1. GET /login para pegar o CSRF token
        HttpResponse<String> loginPage = HTTP.send(
            HttpRequest.newBuilder().uri(URI.create(BASE + "/login")).GET().build(),
            HttpResponse.BodyHandlers.ofString());

        String csrf = loginPage.body()
            .replaceAll("(?s).*name=\"_csrf\"[^>]*value=\"([^\"]+)\".*", "$1");
        String csrfCookie = loginPage.headers().allValues("Set-Cookie").stream()
            .filter(c -> c.startsWith("JSESSIONID")).findFirst().orElse("");
        String jsessionid = csrfCookie.split(";")[0];

        // 2. POST /login com credenciais
        String user = System.getProperty("test.user", "admin");
        String pass = System.getProperty("test.pass", "changeme");
        HttpResponse<String> auth = HTTP.send(
            HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", jsessionid)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "username=" + user + "&password=" + pass + "&_csrf=" + csrf))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        sessionCookie = auth.headers().allValues("Set-Cookie").stream()
            .filter(c -> c.startsWith("JSESSIONID")).findFirst()
            .map(c -> c.split(";")[0]).orElse(jsessionid.split(";")[0]);
    }

    // ── utilitário ────────────────────────────────────────────────────────────

    private HttpResponse<String> get(String path, int timeoutSec) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + path))
                        .timeout(Duration.ofSeconds(timeoutSec))
                        .header("Cookie", sessionCookie)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("Cookie", sessionCookie)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertOkJson(HttpResponse<String> r, String endpoint) {
        assertEquals(200, r.statusCode(), endpoint + " deve retornar 200");
        assertNotNull(r.body(), endpoint + " body não pode ser null");
        assertFalse(r.body().isBlank(), endpoint + " body não pode ser vazio");
        String ct = r.headers().firstValue("Content-Type").orElse("");
        assertTrue(ct.contains("json"), endpoint + " deve retornar Content-Type JSON, foi: " + ct);
    }

    private void assertJsonArray(HttpResponse<String> r, String endpoint) {
        assertOkJson(r, endpoint);
        assertTrue(r.body().trim().startsWith("["), endpoint + " deve retornar JSON array");
    }

    private void assertCors(HttpResponse<String> r, String endpoint) {
        String cors = r.headers().firstValue("Access-Control-Allow-Origin").orElse("");
        assertEquals("*", cors, endpoint + " deve ter CORS header");
    }

    // ── SIGMETs ──────────────────────────────────────────────────────────────

    @Test @DisplayName("GET /redemet_sigmets_json → 200 JSON array")
    void redemet_sigmets_json() throws Exception {
        var r = get("/redemet_sigmets_json", 20);
        assertJsonArray(r, "/redemet_sigmets_json");
        assertCors(r, "/redemet_sigmets_json");
    }

    @Test @DisplayName("GET /redemet_sigmets_all → 200 JSON array")
    void redemet_sigmets_all() throws Exception {
        var r = get("/redemet_sigmets_all", 20);
        assertJsonArray(r, "/redemet_sigmets_all");
    }

    @Test @DisplayName("GET /redemet_sigmets_vizinhos → 200 JSON array")
    void redemet_sigmets_vizinhos() throws Exception {
        var r = get("/redemet_sigmets_vizinhos", 30);
        assertJsonArray(r, "/redemet_sigmets_vizinhos");
        assertCors(r, "/redemet_sigmets_vizinhos");
    }

    @Test @DisplayName("GET /redemet_airmets_json → 200 JSON array")
    void redemet_airmets_json() throws Exception {
        var r = get("/redemet_airmets_json", 20);
        assertJsonArray(r, "/redemet_airmets_json");
    }

    // ── METARs ───────────────────────────────────────────────────────────────

    @Test @DisplayName("GET /metar_top200_sb → 200 JSON array com lat/lon")
    void metar_top200_sb() throws Exception {
        var r = get("/metar_top200_sb", 30);
        assertJsonArray(r, "/metar_top200_sb");
        assertTrue(r.body().contains("\"icao\""), "/metar_top200_sb deve conter campo icao");
        assertTrue(r.body().contains("\"lat\""),  "/metar_top200_sb deve conter campo lat");
    }

    @Test @DisplayName("GET /avisos_aerodromos_count → 200 JSON com count")
    void avisos_aerodromos_count() throws Exception {
        var r = get("/avisos_aerodromos_count", 10);
        assertOkJson(r, "/avisos_aerodromos_count");
        assertTrue(r.body().contains("\"count\""), "deve conter campo count");
    }

    @Test @DisplayName("GET /api/v1/alerts/metar → 200 JSON array")
    void api_alerts_metar() throws Exception {
        var r = get("/api/v1/alerts/metar", 10);
        assertJsonArray(r, "/api/v1/alerts/metar");
    }

    // ── Imagens / frames ─────────────────────────────────────────────────────

    @Test @DisplayName("GET /canal16frames → 200 JSON array")
    void canal16frames() throws Exception {
        var r = get("/canal16frames", 10);
        assertJsonArray(r, "/canal16frames");
    }

    @Test @DisplayName("GET /frames → 200 JSON com campo frames")
    void frames() throws Exception {
        var r = get("/frames", 10);
        assertOkJson(r, "/frames");
        assertTrue(r.body().contains("\"frames\""), "/frames deve conter campo frames");
    }

    // ── HSV / convecção ──────────────────────────────────────────────────────

    @Test @DisplayName("GET /data/hsv_metadata.json → 200 JSON")
    void data_hsv_metadata() throws Exception {
        var r = get("/data/hsv_metadata.json", 10);
        assertOkJson(r, "/data/hsv_metadata.json");
    }

    @Test @DisplayName("GET /data/hsv_optimized.json → 200 JSON ou 404")
    void data_hsv_optimized() throws Exception {
        var r = get("/data/hsv_optimized.json", 10);
        assertTrue(r.statusCode() == 200 || r.statusCode() == 404,
                "/data/hsv_optimized.json deve retornar 200 ou 404");
    }

    @Test @DisplayName("GET /data/convection_hsv.json → 200 JSON ou 404")
    void data_convection_hsv() throws Exception {
        var r = get("/data/convection_hsv.json", 10);
        assertTrue(r.statusCode() == 200 || r.statusCode() == 204 || r.statusCode() == 404,
                "/data/convection_hsv.json deve retornar 200, 204 ou 404");
    }

    @Test @DisplayName("GET /data/convection_hsv_polygon.json → 200 ou 404 (arquivo pode não existir)")
    void data_convection_hsv_polygon() throws Exception {
        var r = get("/data/convection_hsv_polygon.json", 10);
        assertTrue(r.statusCode() == 200 || r.statusCode() == 404,
                "/data/convection_hsv_polygon.json deve retornar 200 ou 404");
    }

    // ── Vento / GRIB2 ────────────────────────────────────────────────────────

    @ParameterizedTest(name = "GET /api/wind/barbs/{0}")
    @ValueSource(strings = {"fl050", "fl100", "fl180", "fl240", "fl300", "fl390"})
    @DisplayName("GET /api/wind/barbs/{level} → 200 JSON array ou 503 (cache não pronto)")
    void api_wind_barbs(String level) throws Exception {
        var r = get("/api/wind/barbs/" + level + "?skip=4", 30);
        assertTrue(r.statusCode() == 200 || r.statusCode() == 503,
                "/api/wind/barbs/" + level + " deve retornar 200 ou 503");
        if (r.statusCode() == 200) {
            assertTrue(r.body().trim().startsWith("["),
                    "/api/wind/barbs/" + level + " deve retornar JSON array");
        }
    }

    @Test @DisplayName("GET /grib2_info → 200 JSON com campo run")
    void grib2_info() throws Exception {
        var r = get("/grib2_info", 10);
        assertOkJson(r, "/grib2_info");
        assertTrue(r.body().contains("\"run\""), "/grib2_info deve conter campo run");
    }

    // ── SigWx ────────────────────────────────────────────────────────────────

    @Test @DisplayName("GET /api/v1/sigwx/list → 200 JSON")
    void sigwx_list() throws Exception {
        var r = get("/api/v1/sigwx/list", 10);
        assertOkJson(r, "/api/v1/sigwx/list");
    }

    @Test @DisplayName("GET /api/v1/sigwx/current → 200 GeoJSON ou 404")
    void sigwx_current() throws Exception {
        var r = get("/api/v1/sigwx/current", 10);
        assertTrue(r.statusCode() == 200 || r.statusCode() == 404,
                "/api/v1/sigwx/current deve retornar 200 ou 404");
        if (r.statusCode() == 200) {
            assertTrue(r.body().contains("FeatureCollection"),
                    "/api/v1/sigwx/current deve retornar GeoJSON FeatureCollection");
        }
    }

    @Test @DisplayName("GET /api/v1/sigwx/meta → 200 JSON com filename ou 404")
    void sigwx_meta() throws Exception {
        var r = get("/api/v1/sigwx/meta", 10);
        assertTrue(r.statusCode() == 200 || r.statusCode() == 404,
                "/api/v1/sigwx/meta deve retornar 200 ou 404");
    }

    // ── Animação ─────────────────────────────────────────────────────────────

    @Test @DisplayName("GET /api/v1/animation/realcada-images?count=12 → 200 JSON com images")
    void animation_realcada_images() throws Exception {
        var r = get("/api/v1/animation/realcada-images?count=12", 10);
        assertOkJson(r, "/api/v1/animation/realcada-images");
        assertTrue(r.body().contains("\"images\""), "deve conter campo images");
    }

    @Test @DisplayName("GET /api/v1/animation/satellite-wind?hours=3 → 200 ou 404")
    void animation_satellite_wind() throws Exception {
        var r = get("/api/v1/animation/satellite-wind?hours=3", 15);
        assertTrue(r.statusCode() == 200 || r.statusCode() == 404,
                "/api/v1/animation/satellite-wind deve retornar 200 ou 404");
    }

    // ── OPMET ────────────────────────────────────────────────────────────────

    @Test @DisplayName("GET /api/v1/opmet/messages → 200 JSON com connected")
    void opmet_messages() throws Exception {
        var r = get("/api/v1/opmet/messages", 10);
        assertOkJson(r, "/api/v1/opmet/messages");
        assertTrue(r.body().contains("\"connected\""), "deve conter campo connected");
    }

    // ── POST endpoints ───────────────────────────────────────────────────────

    @Test @DisplayName("POST /sigmet → 200 JSON com status success")
    void post_sigmet() throws Exception {
        var r = post("/sigmet", "{\"test\":true}");
        assertEquals(200, r.statusCode(), "/sigmet POST deve retornar 200");
        assertTrue(r.body().contains("success"), "/sigmet deve retornar status success");
    }

    @Test @DisplayName("POST /create_sigmet → 200 JSON com id")
    void post_create_sigmet() throws Exception {
        String body = "{\"severity\":\"SEV\",\"flightLevel\":\"FL300\",\"timestamp\":\"2026-01-01T00:00:00Z\"}";
        var r = post("/create_sigmet", body);
        assertEquals(200, r.statusCode(), "/create_sigmet deve retornar 200");
        assertTrue(r.body().contains("\"id\""), "/create_sigmet deve retornar campo id");
    }

    @Test @DisplayName("POST /realcada_hsv → 200 ou 404 (imagem pode não existir)")
    void post_realcada_hsv() throws Exception {
        var r = post("/realcada_hsv", "");
        assertTrue(r.statusCode() == 200 || r.statusCode() == 404,
                "/realcada_hsv deve retornar 200 ou 404");
    }

    @Test @DisplayName("POST /hsv_optimized → 200 ou 404")
    void post_hsv_optimized() throws Exception {
        var r = post("/hsv_optimized", "");
        assertTrue(r.statusCode() == 200 || r.statusCode() == 404,
                "/hsv_optimized deve retornar 200 ou 404");
    }

    // ── CORS em todos os endpoints críticos ──────────────────────────────────

    @ParameterizedTest(name = "CORS em {0}")
    @ValueSource(strings = {
        "/redemet_sigmets_json",
        "/redemet_sigmets_vizinhos",
        "/redemet_airmets_json",
        "/metar_top200_sb",
        "/api/v1/alerts/metar",
        "/canal16frames",
        "/grib2_info"
    })
    @DisplayName("CORS Access-Control-Allow-Origin: * em endpoints críticos")
    void cors_headers(String path) throws Exception {
        var r = get(path, 30);
        assertCors(r, path);
    }
}
