package com.pocsigmet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.pocsigmet.RedisMetarCacheService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

@Service
public class RadarRedemtService {

    private static final Logger log = LoggerFactory.getLogger(RadarRedemtService.class);
    private static final String PHP_URL = "https://redemet.decea.mil.br/old/produtos/radares-meteorologicos/plota_radar.php";
    private static final Pattern RADAR_PATTERN = Pattern.compile(
        "carrega_radar\\(\\d+,\\s*'(\\w+)',\\s*'([^']+)',\\s*([\\d.\\-]+),\\s*([\\d.\\-]+),\\s*([\\d.\\-]+),\\s*([\\d.\\-]+),\\s*([\\d.\\-]+),\\s*([\\d.\\-]+),\\s*[^,]+,\\s*'([^']+)'");

    private static final int MAX_HISTORY = 10;
    private static final String REDIS_PREFIX = "radar:history:";

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private final Map<String, Map<String, Object>> cache = Collections.synchronizedMap(new LinkedHashMap<>());

    private void redisPush(String sigla, String url) {
        try (redis.clients.jedis.Jedis j = RedisMetarCacheService.getJedisPool() != null ? RedisMetarCacheService.getJedisPool().getResource() : null) {
            if (j == null) return;
            String key = REDIS_PREFIX + sigla;
            List<String> existing = j.lrange(key, 0, -1);
            if (!existing.isEmpty() && existing.get(existing.size() - 1).equals(url)) return;
            j.rpush(key, url);
            j.ltrim(key, -MAX_HISTORY, -1);
        } catch (Exception e) {
            log.warn("Redis radar history push: {}", e.getMessage());
        }
    }

    private List<String> redisGet(String sigla) {
        try (redis.clients.jedis.Jedis j = RedisMetarCacheService.getJedisPool() != null ? RedisMetarCacheService.getJedisPool().getResource() : null) {
            if (j == null) return Collections.emptyList();
            return j.lrange(REDIS_PREFIX + sigla, 0, -1);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Scheduled(fixedDelay = 420_000, initialDelay = 5_000)
    public void fetch() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(PHP_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("radar%5B%5D=maxcappi&zoom=0&animar=0&radarNome="))
                .timeout(Duration.ofSeconds(15))
                .build();

            String body = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
            Matcher m = RADAR_PATTERN.matcher(body);
            Map<String, Map<String, Object>> novo = new LinkedHashMap<>();

            while (m.find()) {
                String sigla = m.group(1);
                String url   = "https://redemet.decea.mil.br/" + m.group(2);

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("sigla",  sigla);
                r.put("url",    url);
                r.put("norte",  Double.parseDouble(m.group(3)));
                r.put("leste",  Double.parseDouble(m.group(4)));
                r.put("sul",    Double.parseDouble(m.group(5)));
                r.put("oeste",  Double.parseDouble(m.group(6)));
                r.put("lat",    Double.parseDouble(m.group(7)));
                r.put("lon",    Double.parseDouble(m.group(8)));
                r.put("nome",   m.group(9).replaceAll("<[^>]+>","").replaceAll("Data/Hora:.*","").trim());
                novo.put(sigla, r);

                redisPush(sigla, url);
                log.info("📼 Radar {} histórico: {} frames", sigla, redisGet(sigla).size());
            }

            if (!novo.isEmpty()) {
                cache.clear();
                cache.putAll(novo);
                log.info("✅ Radar REDEMET: {} radares carregados", novo.size());
            }
        } catch (Exception e) {
            log.warn("⚠️ Erro ao buscar radares: {}", e.getMessage());
        }
    }

    public Collection<Map<String, Object>> getAll() { return cache.values(); }

    public Map<String, Object> getOne(String sigla) { return cache.get(sigla); }

    public List<String> getHistory(String sigla) { return redisGet(sigla); }
}
