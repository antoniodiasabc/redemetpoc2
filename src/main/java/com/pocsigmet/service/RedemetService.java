package com.pocsigmet.service;

import com.pocsigmet.RedemetSigmetClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class RedemetService {

    private static final Logger logger = LoggerFactory.getLogger(RedemetService.class);
    private static final ObjectMapper om = new ObjectMapper();

    private final RedemetSigmetClient client;

    public RedemetService(RedemetSigmetClient client) {
        this.client = client;
    }

    public List<Map<String, Object>> getSigmetsByFir(String fir) {
        try {
            String json = client.getSigmetsJson();
            List<Map<String, Object>> all = om.readValue(json, List.class);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> s : all) {
                if (fir.equalsIgnoreCase(String.valueOf(s.get("fir")))) result.add(s);
            }
            return result;
        } catch (Exception e) {
            logger.error("Erro getSigmetsByFir {}: {}", fir, e.getMessage());
            return Collections.emptyList();
        }
    }

    public Map<String, Object> getMetarByIcao(String icao) {
        try {
            com.pocsigmet.RedemetMetarClient metarClient = new com.pocsigmet.RedemetMetarClient();
            String text = metarClient.getLatestMetarOrSpeci(icao);
            return Map.of("icao", icao, "text", text);
        } catch (Exception e) {
            logger.error("Erro getMetarByIcao {}: {}", icao, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public CompletableFuture<List<Map<String, Object>>> getAllSigmetsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String json = client.getSigmetsJson();
                return om.readValue(json, List.class);
            } catch (Exception e) {
                logger.error("Erro getAllSigmetsAsync: {}", e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    private static final String REDIS_SIGMET_KEY = "cache:sigmets";
    private static final int SIGMET_TTL = 300; // 5 minutos
    private volatile String lastValidSigmetsJson = "[]";

    /** Retorna o JSON bruto de SIGMETs — usado pelos endpoints legados do front */
    public String getSigmetsJson() {
        // Redis primeiro
        try (redis.clients.jedis.Jedis j = com.pocsigmet.RedisMetarCacheService.getJedis()) {
            if (j != null) { String cached = j.get(REDIS_SIGMET_KEY); if (cached != null) return cached; }
        } catch (Exception ignored) {}
        try {
            String json = client.getSigmetsJson();
            if (json != null && !json.equals("[]")) {
                lastValidSigmetsJson = json;
                try (redis.clients.jedis.Jedis j = com.pocsigmet.RedisMetarCacheService.getJedis()) {
                    if (j != null) j.setex(REDIS_SIGMET_KEY, SIGMET_TTL, json);
                } catch (Exception ignored) {}
            }
            return lastValidSigmetsJson;
        } catch (Exception e) { logger.error("getSigmetsJson: {}", e.getMessage()); return lastValidSigmetsJson; }
    }

    private static final String REDIS_AIRMET_KEY = "cache:airmets";
    private static final int AIRMET_TTL = 300; // 5 minutos
    private volatile String lastValidAirmetsJson = "[]";

    /** Retorna o JSON bruto de AIRMETs */
    public String getAirmetsJson() {
        // Redis primeiro
        try (redis.clients.jedis.Jedis j = com.pocsigmet.RedisMetarCacheService.getJedis()) {
            if (j != null) { String cached = j.get(REDIS_AIRMET_KEY); if (cached != null) return cached; }
        } catch (Exception ignored) {}
        try {
            String json = client.getAirmetsJson();
            if (json != null && !json.equals("[]")) {
                lastValidAirmetsJson = json;
                try (redis.clients.jedis.Jedis j = com.pocsigmet.RedisMetarCacheService.getJedis()) {
                    if (j != null) j.setex(REDIS_AIRMET_KEY, AIRMET_TTL, json);
                } catch (Exception ignored) {}
            }
            return lastValidAirmetsJson;
        } catch (Exception e) {
            logger.error("getAirmetsJson: {}", e.getMessage());
            return lastValidAirmetsJson;
        }
    }
}
