package com.pocsigmet.test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Testa se é viável buscar as últimas N imagens de radar retroativamente
 * tentando URLs com intervalos de 10min.
 * Executar: javac + java direto, sem Spring.
 */
public class RadarHistoryProbe {

    static final String BASE = "https://redemet.decea.mil.br/old/radar";
    static final String SIGLA = "pc";
    static final int TENTATIVAS = 15; // tenta 15 slots de 10min = ~2.5h
    static final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    public static void main(String[] args) throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<String> encontradas = new ArrayList<>();
        List<String> ausentes    = new ArrayList<>();

        System.out.println("Sondando últimas " + TENTATIVAS + " imagens de radar [" + SIGLA + "] a partir de " + now);
        System.out.println("─".repeat(70));

        for (int i = 0; i < TENTATIVAS; i++) {
            LocalDateTime t = now.minusMinutes(i * 10L);
            // tenta minuto exato e ±2min para compensar variação do radar
            boolean achou = false;
            for (int delta : new int[]{0, -1, 1, -2, 2, -3, 3, -4, 4, -5, 5}) {
                LocalDateTime candidate = t.plusMinutes(delta);
                String url = buildUrl(candidate);
                int status = head(url);
                if (status == 200) {
                    System.out.printf("✅ slot -%02dmin → %s%n", i * 10, url);
                    encontradas.add(url);
                    achou = true;
                    break;
                }
            }
            if (!achou) {
                String url = buildUrl(t);
                System.out.printf("❌ slot -%02dmin → não encontrado (%s)%n", i * 10, url);
                ausentes.add(url);
            }
        }

        System.out.println("─".repeat(70));
        System.out.printf("Resultado: %d encontradas / %d ausentes de %d tentativas%n",
            encontradas.size(), ausentes.size(), TENTATIVAS);
        System.out.println(encontradas.size() >= 5 ? "✅ Abordagem VIÁVEL" : "⚠️  Abordagem INVIÁVEL — poucos frames encontrados");
    }

    static String buildUrl(LocalDateTime t) {
        return String.format("%s/%04d/%02d/%02d/%s/maxcappi/maps/%04d-%02d-%02d--%02d:%02d:00.png",
            BASE, t.getYear(), t.getMonthValue(), t.getDayOfMonth(), SIGLA,
            t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute());
    }

    static int head(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(8))
                .build();
            return client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) { return -1; }
    }
}
