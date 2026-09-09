package com.pocsigmet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

public class EndpointIntegrationTest {
    
    private static final String BASE_URL = System.getProperty("test.base.url", "http://localhost:80");
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    
    @Test
    public void testHealthEndpoint() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .timeout(Duration.ofSeconds(5))
                .build();
                
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            assertTrue(response.statusCode() < 400, "Health endpoint deve responder com sucesso");
            System.out.println("✅ Health: " + response.statusCode());
            
        } catch (Exception e) {
            fail("Erro no teste de health: " + e.getMessage());
        }
    }
    
    @Test
    public void testFramesEndpoint() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/frames"))
                .timeout(Duration.ofSeconds(5))
                .build();
                
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            assertTrue(response.statusCode() < 400, "Frames endpoint deve responder com sucesso");
            System.out.println("✅ Frames: " + response.statusCode());
            
        } catch (Exception e) {
            fail("Erro no teste de frames: " + e.getMessage());
        }
    }
    
    @Test
    public void testCanal16FramesEndpoint() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/canal16frames"))
                .timeout(Duration.ofSeconds(5))
                .build();
                
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            assertTrue(response.statusCode() < 400, "Canal16frames endpoint deve responder com sucesso");
            assertFalse(response.body().isEmpty(), "Canal16frames deve retornar dados");
            System.out.println("✅ Canal16frames: " + response.statusCode() + " - " + response.body().length() + " chars");
            
        } catch (Exception e) {
            fail("Erro no teste de canal16frames: " + e.getMessage());
        }
    }
    
    @Test
    public void testWindBarbsEndpoint() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/wind/barbs/fl050?skip=4"))
                .timeout(Duration.ofSeconds(30)) // Barbelas podem demorar
                .build();
                
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            assertTrue(response.statusCode() < 400, "WindBarbs endpoint deve responder com sucesso");
            assertTrue(response.body().startsWith("["), "WindBarbs deve retornar JSON array");
            System.out.println("✅ WindBarbs FL050: " + response.statusCode() + " - " + response.body().length() + " chars");
            
        } catch (Exception e) {
            fail("Erro no teste de windbarbs: " + e.getMessage());
        }
    }
    
    @Test
    public void testMetarEndpoint() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/metar_top200_sb"))
                .timeout(Duration.ofSeconds(30))
                .build();
                
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            assertTrue(response.statusCode() < 400, "METAR endpoint deve responder com sucesso");
            System.out.println("✅ METAR: " + response.statusCode());
            
        } catch (Exception e) {
            fail("Erro no teste de METAR: " + e.getMessage());
        }
    }
}
