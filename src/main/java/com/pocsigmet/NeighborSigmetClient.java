package com.pocsigmet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consulta SIGMETs das FIRs vizinhas ao Brasil via REDEMET.
 * Usa NeighborSigmetParser para parsear. Não modifica RedemetSigmetClient.
 */
@org.springframework.stereotype.Component
public class NeighborSigmetClient {

    private static final Logger log = LoggerFactory.getLogger(NeighborSigmetClient.class);
    private static final String BASE_URL = "https://opmet.decea.mil.br/redemet/consulta_redemet";
    private static final String LOGIN_URL = "https://opmet.decea.mil.br/adm/login";
    @org.springframework.beans.factory.annotation.Value("${app.redemet.username:redemetwebservice}")
    private String username;
    @org.springframework.beans.factory.annotation.Value("${app.redemet.password:Mudar12345@}")
    private String password;

    // FIRs vizinhas ao Brasil
    private static final List<String> NEIGHBOR_FIRS = Arrays.asList(
        "SPIM",  // Peru
        "SVZM",  // Venezuela
        "SKED",  // Colombia Bogota
        "SKEC",  // Colombia Barranquilla
        "SEFG",  // Equador Guayaquil
        "SLLF",  // Bolivia
        "SGFA",  // Paraguai
        "SARR",  // Argentina Resistencia
        "SACF",  // Argentina Cordoba
        "SAMF",  // Argentina Mendoza
        "SAEF",  // Argentina Ezeiza
        "SAVF",  // Argentina Comodoro
        "SUEO",  // Uruguai
        "SCFZ",  // Chile Antofagasta
        "SCEZ",  // Chile Santiago
        "SCTZ",  // Chile Puerto Montt
        "SCCZ",  // Chile Punta Arenas
        "SCIZ",  // Chile Isla de Pascua
        "SYGC",  // Guiana
        "SOOO",  // Suriname
        "SMPM",  // Guiana Francesa
        "GOOO",  // Dakar Oceanic (vizinha SBAO)
        "TTZP",  // Trinidad e Tobago Piarco (vizinha SBAO/norte)
        "FAJO"   // Johannesburg Oceanic (vizinha SBAO/leste)
    );

    private static String cachedToken = null;
    private static long tokenExpiry = 0;
    private static final Object tokenLock = new Object();

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final NeighborSigmetParser parser;

    public NeighborSigmetClient(NeighborSigmetParser parser) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.mapper = new ObjectMapper();
        this.parser = parser;
    }

    private String getToken() throws IOException, InterruptedException {
        synchronized (tokenLock) {
            if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) return cachedToken;
            String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(LOGIN_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15))
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new RuntimeException("Auth falhou: " + resp.statusCode());
            cachedToken = mapper.readTree(resp.body()).get("authorization").asText().replace("Bearer ", "");
            tokenExpiry = System.currentTimeMillis() + (50 * 60 * 1000);
            return cachedToken;
        }
    }

    private String fetchSigmets(String fir, String token) throws IOException, InterruptedException {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime from = now.minusHours(6);
        LocalDateTime to   = now.plusHours(6);
        String ini = String.format("%04d%02d%02d%02d", from.getYear(), from.getMonthValue(), from.getDayOfMonth(), from.getHour());
        String fim = String.format("%04d%02d%02d%02d", to.getYear(),   to.getMonthValue(),   to.getDayOfMonth(),   to.getHour());
        String url = String.format("%s?local=%s&msg=SIGMET&data_ini=%s&data_fim=%s&data_hora=nao", BASE_URL, fir, ini, fim);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "text/plain")
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(15))
            .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 403) {
            synchronized (tokenLock) { cachedToken = null; tokenExpiry = 0; }
            return fetchSigmets(fir, getToken());
        }
        return resp.statusCode() == 200 ? resp.body() : "";
    }

    /**
     * Retorna GeoJSON FeatureCollection com todos os SIGMETs válidos das FIRs vizinhas.
     */
    public String getNeighborSigmetsJson() throws IOException, InterruptedException {
        String token = getToken();
        List<Map<String, Object>> features = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String fir : NEIGHBOR_FIRS) {
            try {
                String raw = fetchSigmets(fir, token);
                if (raw == null || raw.trim().isEmpty()) continue;

                // Split por '=' — separador de mensagens SIGMET
                String[] messages = raw.split("=");
                for (String msg : messages) {
                    msg = msg.trim();
                    if (!msg.contains("SIGMET")) continue;
                    Map<String, Object> feature = parser.parse(msg, fir);
                    if (feature == null) continue;
                    // Deduplicar por fir+número+período
                    Map<String,Object> props = (Map<String,Object>) feature.get("properties");
                    String key = fir + "|" + props.get("sigmetNumber") + "|" + props.get("validPeriod");
                    if (seen.add(key)) features.add(feature);
                }
            } catch (Exception e) {
                log.warn("⚠️ Erro ao buscar SIGMET vizinho {}: {}", fir, e.getMessage());
            }
        }

        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("features", features);
        log.info("✅ SIGMETs vizinhos: {} features", features.size());
        return mapper.writeValueAsString(features); // retorna array direto como os outros endpoints
    }
}
