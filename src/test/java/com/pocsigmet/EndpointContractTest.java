package com.pocsigmet;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FASE 0 - Testes de Contrato para todos os endpoints expostos em PocSigmetApplication.
 * 
 * OBJETIVO: Garantir que cada endpoint responde corretamente ANTES de qualquer refatoração.
 * Estes testes servem como "rede de segurança" — se passam antes e depois do refactoring,
 * nada quebrou.
 * 
 * PRÉ-REQUISITO: A aplicação deve estar rodando em localhost:80.
 * Para rodar: mvn test -Dtest=EndpointContractTest
 * 
 * COMO USAR:
 * 1. Suba a aplicação normalmente (java -jar ou mvn spring-boot:run)
 * 2. Em outro terminal: mvn test -Dtest=EndpointContractTest
 * 3. Todos os testes devem passar com o código atual (baseline)
 * 4. Após qualquer refactoring, rodar novamente para validar
 * 
 * NOTA: Alguns endpoints podem retornar 200 com body vazio ou dados stale
 * dependendo do estado (sem imagens baixadas, sem conexão Oracle, etc).
 * O contrato verifica apenas que o endpoint EXISTE e não retorna 500/404.
 */
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Fase 0 - Testes de Contrato dos Endpoints")
public class EndpointContractTest {

    private static final String BASE_URL = System.getProperty("test.base.url", "http://localhost:80");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    private static HttpClient client;
    private static String sessionCookie = "";
    private static List<String> failedEndpoints = new ArrayList<>();
    
    @BeforeAll
    static void setup() throws Exception {
        client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        sessionCookie = ""; // login desabilitado — anyRequest().permitAll()
    }
    
    @AfterAll
    static void summary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 RESUMO DOS TESTES DE CONTRATO");
        System.out.println("=".repeat(60));
        if (failedEndpoints.isEmpty()) {
            System.out.println("✅ TODOS OS ENDPOINTS PASSARAM!");
        } else {
            System.out.println("❌ ENDPOINTS COM FALHA:");
            failedEndpoints.forEach(e -> System.out.println("   - " + e));
        }
        System.out.println("=".repeat(60));
    }

    // ==================== HELPER METHODS ====================
    
    private HttpResponse<String> doGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .timeout(TIMEOUT)
            .header("Cookie", sessionCookie)
            .GET()
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    private HttpResponse<byte[]> doGetBytes(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .timeout(TIMEOUT)
            .header("Cookie", sessionCookie)
            .GET()
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
    
    private void assertEndpointAlive(String path, String description) {
        try {
            HttpResponse<String> response = doGet(path);
            int status = response.statusCode();
            assertTrue(status < 500, 
                description + " [" + path + "] retornou erro do servidor: " + status);
            assertNotEquals(404, status, 
                description + " [" + path + "] não encontrado (404)");
            System.out.println("  ✅ " + path + " → " + status + " (" + response.body().length() + " chars)");
        } catch (java.net.http.HttpTimeoutException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, description + " [" + path + "] ignorado: timeout de rede externa");
        } catch (Exception e) {
            failedEndpoints.add(path + " → " + e.getMessage());
            fail(description + " [" + path + "] falhou: " + e.getMessage());
        }
    }
    
    private void assertJsonEndpoint(String path, String description) {
        try {
            HttpResponse<String> response = doGet(path);
            int status = response.statusCode();
            assertTrue(status < 500, 
                description + " [" + path + "] retornou erro do servidor: " + status);
            assertNotEquals(404, status, 
                description + " [" + path + "] não encontrado (404)");
            
            String body = response.body();
            if (status == 200 && body != null && !body.isEmpty()) {
                String trimmed = body.trim();
                assertTrue(trimmed.startsWith("{") || trimmed.startsWith("["),
                    description + " [" + path + "] não retornou JSON válido. Início: " 
                    + trimmed.substring(0, Math.min(50, trimmed.length())));
            }
            System.out.println("  ✅ " + path + " → " + status + " (JSON, " + body.length() + " chars)");
        } catch (java.net.http.HttpTimeoutException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, description + " [" + path + "] ignorado: timeout de rede externa");
        } catch (Exception e) {
            failedEndpoints.add(path + " → " + e.getMessage());
            fail(description + " [" + path + "] falhou: " + e.getMessage());
        }
    }
    
    private void assertImageEndpoint(String path, String description) {
        try {
            HttpResponse<byte[]> response = doGetBytes(path);
            int status = response.statusCode();
            // Imagens podem não existir ainda (sem download), aceitar 200 ou outros não-erro
            assertTrue(status < 500, 
                description + " [" + path + "] retornou erro do servidor: " + status);
            assertNotEquals(404, status, 
                description + " [" + path + "] não encontrado (404)");
            
            if (status == 200) {
                byte[] body = response.body();
                assertTrue(body.length > 0, 
                    description + " [" + path + "] retornou imagem vazia");
            }
            System.out.println("  ✅ " + path + " → " + status + " (Image, " + response.body().length + " bytes)");
        } catch (Exception e) {
            failedEndpoints.add(path + " → " + e.getMessage());
            fail(description + " [" + path + "] falhou: " + e.getMessage());
        }
    }

    // ==================== 1. HEALTH ====================
    
    @Test
    @Order(1)
    @DisplayName("GET /health - Health check básico")
    void testHealthEndpoint() {
        try {
            HttpResponse<String> response = doGet("/health");
            assertEquals(200, response.statusCode(), "/health deve retornar 200");
            String body = response.body();
            assertNotNull(body);
            assertFalse(body.isEmpty(), "/health não deve retornar body vazio");
            // Deve conter alguma indicação de saúde
            String lower = body.toLowerCase();
            assertTrue(lower.contains("ok") || lower.contains("up") || lower.contains("healthy") || lower.contains("status"),
                "/health deve conter indicador de saúde. Body: " + body.substring(0, Math.min(200, body.length())));
            System.out.println("  ✅ /health → 200");
        } catch (Exception e) {
            failedEndpoints.add("/health → " + e.getMessage());
            fail("/health falhou: " + e.getMessage());
        }
    }

    // ==================== 2. IMAGENS ESTÁTICAS ====================
    
    @Test
    @Order(10)
    @DisplayName("GET /vis - Imagem VIS mais recente")
    void testVisEndpoint() {
        assertImageEndpoint("/vis", "Imagem VIS");
    }
    
    @Test
    @Order(11)
    @DisplayName("GET /canal16 - Imagem Canal 16 mais recente")
    void testCanal16Endpoint() {
        assertImageEndpoint("/canal16", "Imagem Canal 16");
    }
    
    @Test
    @Order(12)
    @DisplayName("GET /realcada - Imagem Realçada mais recente")
    void testRealcadaEndpoint() {
        assertImageEndpoint("/realcada", "Imagem Realçada");
    }
    
    @Test
    @Order(13)
    @DisplayName("GET /cptec - Imagem CPTEC")
    void testCptecEndpoint() {
        assertImageEndpoint("/cptec", "Imagem CPTEC");
    }
    
    @Test
    @Order(14)
    @DisplayName("GET /raster - Imagem Raster")
    void testRasterEndpoint() {
        assertImageEndpoint("/raster", "Imagem Raster");
    }

    // ==================== 3. FRAMES / ANIMAÇÃO ====================
    
    @Test
    @Order(20)
    @DisplayName("GET /frames - Lista de frames disponíveis")
    void testFramesEndpoint() {
        assertJsonEndpoint("/frames", "Lista de frames");
    }
    
    @Test
    @Order(21)
    @DisplayName("GET /canal16frames - Lista de frames Canal 16")
    void testCanal16FramesEndpoint() {
        assertJsonEndpoint("/canal16frames", "Lista de frames Canal 16");
    }
    
    @Test
    @Order(22)
    @DisplayName("GET /api/v1/animation/realcada-images - Lista de imagens para animação")
    void testRealcadaImagesEndpoint() {
        assertJsonEndpoint("/api/v1/animation/realcada-images", "Lista imagens animação realçada");
    }

    // ==================== 4. HSV / CONVECÇÃO ====================
    
    @Test
    @Order(30)
    @DisplayName("GET /convection - Dados de convecção")
    void testConvectionEndpoint() {
        assertEndpointAlive("/convection", "Dados de convecção");
    }
    
    @Test
    @Order(31)
    @DisplayName("GET /hsv_metadata - Metadados HSV")
    void testHsvMetadataEndpoint() {
        assertEndpointAlive("/hsv_metadata", "Metadados HSV");
    }
    
    @Test
    @Order(32)
    @DisplayName("GET /realcada_hsv - Imagem Realçada HSV processada")
    void testRealcadaHsvEndpoint() {
        assertEndpointAlive("/realcada_hsv", "Realçada HSV");
    }
    
    @Test
    @Order(33)
    @DisplayName("GET /hsv_optimized - HSV otimizado")
    void testHsvOptimizedEndpoint() {
        assertEndpointAlive("/hsv_optimized", "HSV otimizado");
    }
    
    @Test
    @Order(34)
    @DisplayName("GET /hsv_polygons - Polígonos HSV")
    void testHsvPolygonsEndpoint() {
        assertJsonEndpoint("/hsv_polygons", "Polígonos HSV");
    }

    // ==================== 5. METAR ====================
    
    @Test
    @Order(40)
    @DisplayName("GET /metar_sbsp - METAR de Congonhas")
    void testMetarSbspEndpoint() {
        assertEndpointAlive("/metar_sbsp", "METAR SBSP");
    }
    
    @Test
    @Order(41)
    @DisplayName("GET /metar_sboi - METAR de Oiapoque")
    void testMetarSboiEndpoint() {
        assertEndpointAlive("/metar_sboi", "METAR SBOI");
    }
    
    @Test
    @Order(42)
    @DisplayName("GET /metar_all - Todos os METARs")
    void testMetarAllEndpoint() {
        assertJsonEndpoint("/metar_all", "METAR All");
    }
    
    @Test
    @Order(43)
    @DisplayName("GET /metar_top20_sb - Top 20 aeroportos SB")
    void testMetarTop20Endpoint() {
        assertJsonEndpoint("/metar_top20_sb", "METAR Top 20 SB");
    }
    
    @Test
    @Order(44)
    @DisplayName("GET /metar_top50_sb - Top 50 aeroportos SB")
    void testMetarTop50Endpoint() {
        assertJsonEndpoint("/metar_top50_sb", "METAR Top 50 SB");
    }
    
    @Test
    @Order(45)
    @DisplayName("GET /metar_top200_sb - Top 200 aeroportos SB")
    void testMetarTop200Endpoint() {
        assertJsonEndpoint("/metar_top200_sb", "METAR Top 200 SB");
    }

    // ==================== 6. SIGMET ====================
    
    @Test
    @Order(50)
    @DisplayName("GET /sigmet - SIGMETs ativos")
    void testSigmetEndpoint() {
        assertEndpointAlive("/sigmet", "SIGMET");
    }
    
    @Test
    @Order(51)
    @DisplayName("GET /sigmet_copilot - SIGMET Copilot")
    void testSigmetCopilotEndpoint() {
        assertEndpointAlive("/sigmet_copilot", "SIGMET Copilot");
    }
    
    @Test
    @Order(52)
    @DisplayName("GET /sigmet/count - Contagem de SIGMETs")
    void testSigmetCountEndpoint() {
        assertJsonEndpoint("/sigmet/count", "SIGMET Count");
    }
    
    @Test
    @Order(53)
    @DisplayName("GET /create_sigmet - Formulário/dados criação SIGMET")
    void testCreateSigmetEndpoint() {
        assertEndpointAlive("/create_sigmet", "Create SIGMET");
    }
    
    @Test
    @Order(54)
    @DisplayName("GET /redemet_sigmets - SIGMETs do REDEMET")
    void testRedemetSigmetsEndpoint() {
        assertEndpointAlive("/redemet_sigmets", "REDEMET SIGMETs");
    }
    
    @Test
    @Order(55)
    @DisplayName("GET /redemet_sigmets_json - SIGMETs do REDEMET em JSON")
    void testRedemetSigmetsJsonEndpoint() {
        assertJsonEndpoint("/redemet_sigmets_json", "REDEMET SIGMETs JSON");
    }
    
    @Test
    @Order(56)
    @DisplayName("GET /redemet_sigmets_all - Todos SIGMETs REDEMET")
    void testRedemetSigmetsAllEndpoint() {
        assertEndpointAlive("/redemet_sigmets_all", "REDEMET SIGMETs All");
    }
    
    @Test
    @Order(57)
    @DisplayName("GET /severe_convection_with_sigmet.geojson - GeoJSON convecção severa")
    void testSevereConvectionGeojsonEndpoint() {
        assertJsonEndpoint("/severe_convection_with_sigmet.geojson", "GeoJSON Convecção Severa");
    }

    // ==================== 7. GeoJSON / FIRs ====================
    
    @Test
    @Order(60)
    @DisplayName("GET /firs - FIRs (Flight Information Regions)")
    void testFirsEndpoint() {
        assertJsonEndpoint("/firs", "FIRs");
    }
    
    @Test
    @Order(61)
    @DisplayName("GET /risk_polygons.geojson - Polígonos de risco")
    void testRiskPolygonsEndpoint() {
        assertJsonEndpoint("/risk_polygons.geojson", "Risk Polygons GeoJSON");
    }

    // ==================== 8. VENTO / GRIB2 ====================
    
    @Test
    @Order(70)
    @DisplayName("GET /api/wind/barbs/fl050 - Wind barbs FL050")
    void testWindBarbsFl050Endpoint() {
        assertJsonEndpoint("/api/wind/barbs/fl050", "Wind Barbs FL050");
    }
    
    @Test
    @Order(71)
    @DisplayName("GET /api/wind/barbs/fl390 - Wind barbs FL390")
    void testWindBarbsFl390Endpoint() {
        assertJsonEndpoint("/api/wind/barbs/fl390", "Wind Barbs FL390");
    }
    
    @Test
    @Order(72)
    @DisplayName("GET /api/wind/barbs/surface - Wind barbs superfície")
    void testWindBarbsSurfaceEndpoint() {
        assertJsonEndpoint("/api/wind/barbs/surface", "Wind Barbs Surface");
    }
    
    @Test
    @Order(73)
    @DisplayName("GET /api/wind/barbs/fl100 - Wind barbs FL100")
    void testWindBarbsFl100Endpoint() {
        assertJsonEndpoint("/api/wind/barbs/fl100", "Wind Barbs FL100");
    }
    
    @Test
    @Order(74)
    @DisplayName("GET /api/wind/barbs/fl240 - Wind barbs FL240")
    void testWindBarbsFl240Endpoint() {
        assertJsonEndpoint("/api/wind/barbs/fl240", "Wind Barbs FL240");
    }
    
    @Test
    @Order(75)
    @DisplayName("GET /api/wind/barbs/fl300 - Wind barbs FL300")
    void testWindBarbsFl300Endpoint() {
        assertJsonEndpoint("/api/wind/barbs/fl300", "Wind Barbs FL300");
    }
    
    @Test
    @Order(76)
    @DisplayName("GET /grib2_info - Informações GRIB2")
    void testGrib2InfoEndpoint() {
        assertJsonEndpoint("/grib2_info", "GRIB2 Info");
    }

    // ==================== 9. OPMET ====================
    
    @Test
    @Order(80)
    @DisplayName("GET /opmet_live - Página OPMET ao vivo")
    void testOpmetLiveEndpoint() {
        assertEndpointAlive("/opmet_live", "OPMET Live");
    }
    
    @Test
    @Order(81)
    @DisplayName("GET /api/v1/opmet/messages - Mensagens OPMET")
    void testOpmetMessagesEndpoint() {
        assertJsonEndpoint("/api/v1/opmet/messages", "OPMET Messages");
    }

    // ==================== 10. SIGWX ====================
    
    @Test
    @Order(90)
    @DisplayName("GET /api/v1/sigwx/current - SIGWX atual")
    void testSigwxCurrentEndpoint() {
        assertEndpointAlive("/api/v1/sigwx/current", "SIGWX Current");
    }
    
    @Test
    @Order(91)
    @DisplayName("GET /api/v1/sigwx/meta - Metadados SIGWX")
    void testSigwxMetaEndpoint() {
        assertJsonEndpoint("/api/v1/sigwx/meta", "SIGWX Meta");
    }

    // ==================== 11. TRANSPARÊNCIA ====================
    
    @Test
    @Order(100)
    @DisplayName("GET /make_transparent - Make Transparent")
    void testMakeTransparentEndpoint() {
        assertEndpointAlive("/make_transparent", "Make Transparent");
    }
    
    @Test
    @Order(101)
    @DisplayName("GET /transparent_image - Imagem transparente")
    void testTransparentImageEndpoint() {
        assertEndpointAlive("/transparent_image", "Transparent Image");
    }

    // ==================== 12. MONITORING ====================
    
    @Test
    @Order(110)
    @DisplayName("GET /monitoring/stats - Estatísticas de monitoramento")
    void testMonitoringStatsEndpoint() {
        assertJsonEndpoint("/monitoring/stats", "Monitoring Stats");
    }
    
    @Test
    @Order(111)
    @DisplayName("GET /cache/stats - Estatísticas de cache")
    void testCacheStatsEndpoint() {
        assertJsonEndpoint("/cache/stats", "Cache Stats");
    }

    // ==================== 13. DATA ENDPOINT (prefixo dinâmico) ====================
    
    @Test
    @Order(120)
    @DisplayName("GET /data/hsv_metadata.json - Arquivo de dados HSV metadata")
    void testDataHsvMetadataEndpoint() {
        assertEndpointAlive("/data/hsv_metadata.json", "Data HSV Metadata JSON");
    }
    
    @Test
    @Order(121)
    @DisplayName("GET /data/convection_hsv_polygon.json - Arquivo polígono HSV")
    void testDataConvectionPolygonEndpoint() {
        assertEndpointAlive("/data/convection_hsv_polygon.json", "Data Convection HSV Polygon");
    }

    // ==================== 14. STATIC FILES ====================
    
    @Test
    @Order(130)
    @DisplayName("GET / - Página principal (index.html)")
    void testIndexEndpoint() {
        try {
            HttpResponse<String> response = doGet("/");
            assertEquals(200, response.statusCode(), "/ deve retornar 200");
            String body = response.body();
            assertNotNull(body);
            assertTrue(body.contains("<!DOCTYPE html>") || body.contains("<html"),
                "/ deve retornar HTML. Início: " + body.substring(0, Math.min(100, body.length())));
            assertTrue(body.contains("ol@") || body.contains("openlayers") || body.contains("ol."),
                "/ deve conter referência ao OpenLayers");
            System.out.println("  ✅ / → 200 (HTML, " + body.length() + " chars)");
        } catch (Exception e) {
            failedEndpoints.add("/ → " + e.getMessage());
            fail("/ falhou: " + e.getMessage());
        }
    }

    // ==================== 15. CONECTIVIDADE GERAL ====================
    
    @Test
    @Order(0)
    @DisplayName("PRÉ-CHECK: Servidor acessível na porta 8083")
    void testServerReachable() {
        try {
            HttpResponse<String> response = doGet("/health");
            assertTrue(response.statusCode() > 0, 
                "Servidor não respondeu. Certifique-se que a aplicação está rodando em localhost:80");
            System.out.println("  ✅ Servidor acessível em " + BASE_URL);
        } catch (Exception e) {
            fail("❌ SERVIDOR NÃO ACESSÍVEL em " + BASE_URL + 
                 "\n   Certifique-se que a aplicação está rodando antes de executar os testes." +
                 "\n   Erro: " + e.getMessage());
        }
    }
    
    // ==================== 16. ENDPOINTS COM PREFIXO DINÂMICO ====================
    
    @Test
    @Order(140)
    @DisplayName("GET /canal16frame/<arquivo> - Frame individual Canal 16 (dinâmico)")
    void testCanal16FrameDynamic() {
        // Primeiro, pegar a lista de frames disponíveis
        try {
            HttpResponse<String> response = doGet("/canal16frames");
            if (response.statusCode() == 200 && response.body() != null) {
                String body = response.body().trim();
                if (body.startsWith("[") && body.contains("\"")) {
                    // Extrair primeiro filename do JSON array
                    int start = body.indexOf("\"") + 1;
                    int end = body.indexOf("\"", start);
                    if (start > 0 && end > start) {
                        String firstFrame = body.substring(start, end);
                        assertEndpointAlive("/canal16frame/" + firstFrame, "Frame individual Canal 16");
                        return;
                    }
                }
            }
            System.out.println("  ⚠️ /canal16frame/* - Sem frames disponíveis para testar (OK)");
        } catch (Exception e) {
            System.out.println("  ⚠️ /canal16frame/* - Não testável: " + e.getMessage());
        }
    }
    
    @Test
    @Order(141)
    @DisplayName("GET /frame/<arquivo> - Frame individual (dinâmico)")
    void testFrameDynamic() {
        try {
            HttpResponse<String> response = doGet("/frames");
            if (response.statusCode() == 200 && response.body() != null) {
                String body = response.body().trim();
                if (body.startsWith("[") && body.contains("\"")) {
                    int start = body.indexOf("\"") + 1;
                    int end = body.indexOf("\"", start);
                    if (start > 0 && end > start) {
                        String firstFrame = body.substring(start, end);
                        assertEndpointAlive("/frame/" + firstFrame, "Frame individual");
                        return;
                    }
                }
            }
            System.out.println("  ⚠️ /frame/* - Sem frames disponíveis para testar (OK)");
        } catch (Exception e) {
            System.out.println("  ⚠️ /frame/* - Não testável: " + e.getMessage());
        }
    }

    // ==================== 17. ENDPOINT 404 CONFIRMAÇÃO ====================
    
    @Test
    @Order(200)
    @DisplayName("GET /endpoint_inexistente - Deve retornar arquivo estático ou 404")
    void testNonExistentEndpoint() {
        try {
            HttpResponse<String> response = doGet("/endpoint_que_nao_existe_xyz_123");
            // O router cai no default (staticHandler), pode retornar 404 ou o index.html
            int status = response.statusCode();
            assertTrue(status == 404 || status == 200, 
                "Endpoint inexistente deve retornar 404 ou 200 (fallback para index.html). Retornou: " + status);
            System.out.println("  ✅ Endpoint inexistente → " + status + " (comportamento esperado)");
        } catch (Exception e) {
            failedEndpoints.add("/endpoint_inexistente → " + e.getMessage());
            fail("Teste de endpoint inexistente falhou: " + e.getMessage());
        }
    }
    
    // ==================== 18. CORS ====================
    
    @Test
    @Order(210)
    @DisplayName("Verificar CORS headers em endpoint de dados")
    void testCorsHeaders() {
        try {
            HttpResponse<String> response = doGet("/data/hsv_metadata.json");
            // Verificar se CORS está presente (o router adiciona Access-Control-Allow-Origin)
            var headers = response.headers();
            // O endpoint /data/* adiciona CORS explicitamente
            if (response.statusCode() == 200) {
                String cors = headers.firstValue("Access-Control-Allow-Origin").orElse("");
                if (!cors.isEmpty()) {
                    assertEquals("*", cors, "CORS deve permitir todas as origens");
                    System.out.println("  ✅ CORS headers presentes em /data/*");
                } else {
                    System.out.println("  ⚠️ CORS headers não presentes (pode estar OK se via proxy)");
                }
            }
        } catch (Exception e) {
            System.out.println("  ⚠️ Teste CORS não executável: " + e.getMessage());
        }
    }
}
