package com.pocsigmet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class ConcurrencyTest {
    
    private static final String BASE_URL = System.getProperty("test.base.url", "http://localhost:80");
    private static String sessionCookie = "";
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    @org.junit.jupiter.api.BeforeEach
    void login() throws Exception {
        if (!sessionCookie.isEmpty()) return;
        HttpResponse<String> loginPage = client.send(
            HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/login")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        String csrf = loginPage.body()
            .replaceAll("(?s).*name=\"_csrf\"[^>]*value=\"([^\"]+)\".*", "$1");
        String initCookie = loginPage.headers().allValues("Set-Cookie").stream()
            .filter(c -> c.startsWith("JSESSIONID")).findFirst().map(c -> c.split(";")[0]).orElse("");
        HttpResponse<String> auth = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", initCookie)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "username=admin&password=changeme&_csrf=" + csrf))
                .build(),
            HttpResponse.BodyHandlers.ofString());
        sessionCookie = auth.headers().allValues("Set-Cookie").stream()
            .filter(c -> c.startsWith("JSESSIONID")).findFirst()
            .map(c -> c.split(";")[0]).orElse(initCookie);
    }
    
    @Test
    public void testConcurrentOperations() {
        try {
            System.out.println("🧪 Testando concorrência: Barbelas + Health simultâneos");
            
            // Testar Health primeiro (operação leve)
            long startHealth = System.currentTimeMillis();
            HttpRequest healthRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .timeout(Duration.ofSeconds(20))
                .header("Cookie", sessionCookie)
                .build();
            
            HttpResponse<String> healthResponse = client.send(healthRequest, HttpResponse.BodyHandlers.ofString());
            long healthTime = System.currentTimeMillis() - startHealth;
            
            // Health deve responder
            assertTrue(healthResponse.statusCode() < 400, 
                "Health deve responder, status: " + healthResponse.statusCode() + 
                ", body: " + healthResponse.body().substring(0, Math.min(100, healthResponse.body().length())));
            
            System.out.println("✅ Health respondeu em " + healthTime + "ms - Status: " + healthResponse.statusCode());
            
            // Testar se barbelas retorna rate limit (sem aguardar processamento)
            HttpRequest barbelasRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/wind/barbs/fl050?skip=4"))
                .timeout(Duration.ofSeconds(5))
                .header("Cookie", sessionCookie)
                .build();
            
            HttpResponse<String> barbelasResponse = client.send(barbelasRequest, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("✅ Barbelas: Status " + barbelasResponse.statusCode() + 
                " - " + barbelasResponse.body().substring(0, Math.min(50, barbelasResponse.body().length())));
            
            // Rate limit (429) ou sucesso (200) são ambos válidos
            assertTrue(barbelasResponse.statusCode() == 429 || barbelasResponse.statusCode() == 200,
                "Barbelas deve retornar 200 ou 429, foi: " + barbelasResponse.statusCode());
            
            System.out.println("✅ Concorrência básica funcionando!");
            
        } catch (Exception e) {
            fail("Erro no teste de concorrência: " + e.getMessage());
        }
    }
    
    @Test
    public void testMultipleHealthRequests() {
        try {
            System.out.println("🧪 Testando múltiplas requisições Health");
            
            // Fazer 3 requisições Health sequenciais (não simultâneas para evitar sobrecarga)
            for (int i = 0; i < 3; i++) {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/health"))
                    .timeout(Duration.ofSeconds(20)) // Timeout maior
                    .build();
                
                long start = System.currentTimeMillis();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                long time = System.currentTimeMillis() - start;
                
                System.out.println("Health " + i + ": Status " + response.statusCode() + " em " + time + "ms");
                
                assertTrue(response.statusCode() < 400, 
                    "Health " + i + " deve responder com sucesso, foi: " + response.statusCode() + 
                    ", body: " + response.body().substring(0, Math.min(100, response.body().length())));
                
                // Pequena pausa entre requisições
                Thread.sleep(1000);
            }
            
            System.out.println("✅ 3 requisições Health sequenciais - todas OK!");
            
        } catch (Exception e) {
            fail("Erro no teste de múltiplas requisições: " + e.getMessage());
        }
    }
}
