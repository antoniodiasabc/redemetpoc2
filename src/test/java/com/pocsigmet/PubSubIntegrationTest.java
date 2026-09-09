package com.pocsigmet;

import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled;

/**
 * Teste de integração do Redis pub/sub para broadcast WebSocket multi-instância.
 *
 * Pré-requisito: aplicação rodando com 2 instâncias via docker compose.
 *   docker compose up --scale app=2
 *
 * Execução isolada:
 *   mvn test -Dtest=PubSubIntegrationTest -Dintegration.test=true
 */
@Disabled("Requer docker compose com 2 instâncias rodando")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PubSubIntegrationTest {

    // IPs diretos dos containers (bypassam nginx)
    private static final String APP1 = System.getProperty("app1.host", "172.18.0.8");
    private static final String APP2 = System.getProperty("app2.host", "172.18.0.9");
    private static final int    PORT = 8082;
    private static final int    TIMEOUT_MS = 5000;

    /** Verifica que ambas as instâncias estão saudáveis antes de rodar */
    @Test @Order(1)
    void both_instances_healthy() throws Exception {
        assertEquals(200, httpGet("http://" + APP1 + ":" + PORT + "/health").code, "app1 não está saudável");
        assertEquals(200, httpGet("http://" + APP2 + ":" + PORT + "/health").code, "app2 não está saudável");
    }

    /**
     * Cenário principal: mensagem publicada no app1 deve chegar no SSE do app2.
     * Prova que o pub/sub Redis está funcionando entre instâncias.
     */
    @Test @Order(2)
    void message_published_on_app1_received_on_app2_sse() throws Exception {
        List<String> receivedOnApp2 = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        // 1. Conecta SSE no app2
        Thread sseThread = new Thread(() -> {
            try {
                URL url = new URL("http://" + APP2 + ":" + PORT + "/api/v1/opmet/stream");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.connect();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:") && line.contains("SPECI")) {
                            receivedOnApp2.add(line);
                            latch.countDown();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // timeout esperado após latch
            }
        });
        sseThread.setDaemon(true);
        sseThread.start();

        // 2. Aguarda SSE conectar
        Thread.sleep(1000);

        // 3. Dispara mensagem no app1
        HttpResponse resp = httpGet("http://" + APP1 + ":" + PORT + "/api/v1/opmet/test-message");
        assertEquals(200, resp.code, "Falha ao disparar mensagem no app1");

        // 4. Verifica que app2 recebeu via pub/sub
        boolean received = latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertTrue(received, "app2 NÃO recebeu a mensagem publicada no app1 — pub/sub não está funcionando");
        assertFalse(receivedOnApp2.isEmpty(), "Lista de mensagens recebidas no app2 está vazia");
    }

    /**
     * Cenário inverso: mensagem no app2 deve chegar no SSE do app1.
     */
    @Test @Order(3)
    void message_published_on_app2_received_on_app1_sse() throws Exception {
        List<String> receivedOnApp1 = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread sseThread = new Thread(() -> {
            try {
                URL url = new URL("http://" + APP1 + ":" + PORT + "/api/v1/opmet/stream");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.connect();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:") && line.contains("SPECI")) {
                            receivedOnApp1.add(line);
                            latch.countDown();
                            break;
                        }
                    }
                }
            } catch (Exception e) { /* timeout esperado */ }
        });
        sseThread.setDaemon(true);
        sseThread.start();

        Thread.sleep(1000);

        HttpResponse resp = httpGet("http://" + APP2 + ":" + PORT + "/api/v1/opmet/test-message");
        assertEquals(200, resp.code, "Falha ao disparar mensagem no app2");

        boolean received = latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertTrue(received, "app1 NÃO recebeu a mensagem publicada no app2 — pub/sub não está funcionando");
    }

    /**
     * Garante que a mensagem não é duplicada no app1 (não recebe sua própria publicação).
     */
    @Test @Order(4)
    void no_duplicate_on_publisher_instance() throws Exception {
        // usa um token único para identificar esta mensagem específica
        String uniqueToken = "TESTDUP_" + System.currentTimeMillis();
        List<String> receivedOnApp1 = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        // endpoint que aceita mensagem customizada
        Thread sseThread = new Thread(() -> {
            try {
                URL url = new URL("http://" + APP1 + ":" + PORT + "/api/v1/opmet/stream");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.connect();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:") && line.contains(uniqueToken)) {
                            receivedOnApp1.add(line);
                            latch.countDown();
                        }
                    }
                }
            } catch (Exception e) { /* timeout esperado */ }
        });
        sseThread.setDaemon(true);
        sseThread.start();

        Thread.sleep(1000);

        // dispara mensagem com token único via endpoint de simulate
        httpGet("http://" + APP1 + ":" + PORT + "/api/v1/opmet/simulate?msg=" + uniqueToken);

        // aguarda 2s — se receber 2x o latch chega a 0
        boolean duplicate = latch.await(2000, TimeUnit.MILLISECONDS);
        assertFalse(duplicate, "Mensagem duplicada no app1 — pub/sub está reenviando para o próprio publisher");
        assertEquals(1, receivedOnApp1.size(), "app1 deveria receber exatamente 1 vez, recebeu: " + receivedOnApp1.size());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private HttpResponse httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        int code = conn.getResponseCode();
        conn.disconnect();
        return new HttpResponse(code);
    }

    private record HttpResponse(int code) {}
}
