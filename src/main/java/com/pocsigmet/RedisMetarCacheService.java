package com.pocsigmet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache Redis compartilhado para METARs
 */
@org.springframework.stereotype.Service
public class RedisMetarCacheService {
    private static final Logger log = LoggerFactory.getLogger(RedisMetarCacheService.class);

    // Injetado pelo MongoMetarFallbackInjector no startup
    static MongoMetarFallback mongo;

    // Injetado pelo RedisMetarCacheServiceInjector no startup
    static RedemetMetarClient METAR_CLIENT;
    static RedemetSigmetClient SIGMET_CLIENT;
    
    private static JedisPool jedisPool;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Fallback cache em memória
    private static final ConcurrentHashMap<String, CachedMetarData> memoryCache = new ConcurrentHashMap<>();
    
    public static final long METAR_TTL_SECONDS = 360;
    
    static {
        log.info("Inicializando Redis cache...");
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(20);
            poolConfig.setMaxIdle(10);
            poolConfig.setMinIdle(2);
            jedisPool = new JedisPool(poolConfig, System.getenv().getOrDefault("REDIS_HOST", "redis"), 6379);
            // Limpar locks órfãos de restarts anteriores
            try (Jedis j = jedisPool.getResource()) { j.del("lock:download", "lock:grib2"); }
            log.info("Redis conectado");
        } catch (Exception e) {
            log.warn("Redis não disponível, usando cache local: {}", e.getMessage());
        }
    }
    
    public static JedisPool getJedisPool() { return jedisPool; }

    public static class CachedMetarData {
        public String condition;
        public String metarText;
        public String tafText;
        public boolean hasAviso;
        public long timestamp;
        
        public CachedMetarData() {} // Para Jackson
        
        CachedMetarData(String condition, String metarText, String tafText, boolean hasAviso) {
            this.condition = condition;
            this.metarText = metarText;
            this.tafText = tafText;
            this.hasAviso = hasAviso;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired(long ttlSeconds) {
            return System.currentTimeMillis() - timestamp > (ttlSeconds * 1000);
        }
    }
    
    public static java.util.Map<String, CachedMetarData> getMemoryCache() {
        return memoryCache;
    }

    static String deriveConditionFromText(String metarText) {
        String line = metarText.split("\n")[0];
        String[] parts = line.split(" ");
        int visibility = 9999, ceiling = 9999;
        for (String p : parts) {
            if (p.matches("\\d{4}") && !p.endsWith("Z")) { try { visibility = Integer.parseInt(p); } catch (Exception ignored) {} break; }
        }
        for (String p : parts) {
            if (p.startsWith("BKN") || p.startsWith("OVC")) { try { ceiling = Integer.parseInt(p.substring(3, 6)) * 100; } catch (Exception ignored) {} break; }
        }
        if (visibility < 1000 || ceiling < 200) return "LIFR";
        if (visibility < 3000 || ceiling < 500) return "IFR";
        if (visibility < 5000 || ceiling < 1500) return "MVFR";
        return "VFR";
    }

    public static CachedMetarData getStaleMetar(String icao) {
        String key = "metar:" + icao;
        try (Jedis jedis = jedisPool != null ? jedisPool.getResource() : null) {
            if (jedis != null) {
                String cached = jedis.get(key);
                if (cached != null) return objectMapper.readValue(cached, CachedMetarData.class);
            }
        } catch (Exception ignored) {}
        return memoryCache.get(key); // stale local
    }

    public static CachedMetarData getCachedMetar(String icao) {
        String key = "metar:" + icao;
        
        // Tentar Redis primeiro
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                String cached = jedis.get(key);
                if (cached != null) {
                    CachedMetarData data = objectMapper.readValue(cached, CachedMetarData.class);
                    // corrigir UNKNOWN em cache se metarText válido
                    if (data.condition.equals("UNKNOWN") && data.metarText != null && !data.metarText.contains("não disponível")) {
                        data.condition = deriveConditionFromText(data.metarText);
                    }
                    if (!data.isExpired(METAR_TTL_SECONDS)) {
                        return data;
                    }
                }
            } catch (Exception e) {
                log.warn("Redis error: {}", e.getMessage());
            }
        }
        
        // Fallback para cache local
        CachedMetarData cached = memoryCache.get(key);
        if (cached != null && !cached.isExpired(METAR_TTL_SECONDS)) {
            return cached;
        }

        // Fallback MongoDB — retorna sempre, independente da idade (warmup atualiza em background)
        CachedMetarData mongoData = mongo != null ? mongo.load(icao) : null;
        if (mongoData != null) {
            log.info("MongoDB fallback HIT para {}", icao);
            return mongoData;
        }

        // Sem dado em nenhum cache — retorna UNKNOWN (warmup vai popular no próximo ciclo)
        return new CachedMetarData("UNKNOWN", "METAR/SPECI não disponível", "", false);
    }

    // Força busca na REDEMET e salva no Redis — usado pelo scheduler de warmup
    public static void forceRefresh(String icao) throws Exception {
        forceRefresh(icao, false);
    }

    public static void forceRefresh(String icao, boolean forceApi) throws Exception {
        String key = "metar:" + icao;

        // 1. METAR — salva imediatamente no Redis ao chegar
        String latestMessage = METAR_CLIENT.getLatestMetarOrSpeci(icao);
        if (latestMessage == null || latestMessage.contains("não disponível")) {
            return; // mantém o que já está no Redis/memória
        }
        String condition = deriveConditionFromText(latestMessage);
        // salva METAR imediatamente — TAF e avisos enriquecem depois
        CachedMetarData data = new CachedMetarData(condition, latestMessage, "TAF não disponível", false);
        saveToRedis(key, data);

        // 2. Avisos — atualiza registro já salvo
        try {
            boolean hasAviso = METAR_CLIENT.hasAvisoForAirport(icao);
            String finalText = latestMessage;
            if (hasAviso) {
                String avisoText = METAR_CLIENT.getAvisoTextForAirport(icao);
                if (!avisoText.isEmpty()) finalText += "\n\n⚠️ AVISO: " + avisoText;
            }
            data = new CachedMetarData(condition, finalText, data.tafText, hasAviso);
            saveToRedis(key, data);
        } catch (Exception ignored) {}

        // 3. TAF — atualiza registro já salvo
        try {
            String taf = SIGMET_CLIENT.getTafForAirport(icao);
            if (taf != null && !taf.trim().isEmpty()) {
                data = new CachedMetarData(data.condition, data.metarText, taf.trim(), data.hasAviso);
                saveToRedis(key, data);
            }
        } catch (Exception ignored) {}

        memoryCache.put(key, data);
        if (mongo != null) mongo.save(icao, data);
    }
    
    private static void saveToRedis(String key, CachedMetarData data) {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                String json = objectMapper.writeValueAsString(data);
                jedis.setex(key, (int) METAR_TTL_SECONDS, json);
                // espelho no hash global — leitura atômica para alertas
                String icao = key.replace("metar:", "");
                jedis.hset("metar:all", icao, json);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Salva no Redis/memória um METAR já obtido via bulk.
     * Se metarText for "não disponível", não salva — deixa buscar individualmente no próximo request.
     */
    public static void saveFromBulk(String icao, String metarText, String allAvisos) throws Exception {
        if (metarText == null || metarText.contains("não disponível")) return;
        String condition = deriveConditionFromText(metarText);
        String tafText = "";
        try {
            String taf = SIGMET_CLIENT.getTafForAirport(icao);
            if (taf != null && !taf.trim().isEmpty()) tafText = taf.trim();
        } catch (Exception ignored) {}

        boolean hasAviso = false;
        String finalText = metarText;
        if (allAvisos == null) {
            // falha ao buscar avisos — herda do cache anterior
            CachedMetarData prev = getCachedMetarIfPresent(icao);
            if (prev != null) { hasAviso = prev.hasAviso; if (hasAviso) finalText = prev.metarText; }
        } else if (!allAvisos.isEmpty()) {
            for (String aviso : allAvisos.split("=")) {
                if (aviso.contains(icao)) { hasAviso = true; finalText += "\n\n⚠️ AVISO: " + aviso.trim(); break; }
            }
        }

        CachedMetarData data = new CachedMetarData(condition, finalText, tafText, hasAviso);
        String key = "metar:" + icao;
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                String json = objectMapper.writeValueAsString(data);
                jedis.setex(key, (int) METAR_TTL_SECONDS, json);
                jedis.hset("metar:all", icao, json);
            }
        }
        memoryCache.put(key, data);
        if (mongo != null) mongo.save(icao, data);
    }

    public static boolean hasCachedMetar(String icao) {
        String key = "metar:" + icao;
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.exists(key);
            } catch (Exception ignored) {}
        }
        return memoryCache.containsKey(key);
    }

    /** Retorna dado do Redis/memória sem chamar a REDEMET. Retorna null se não existir. */
    public static CachedMetarData getCachedMetarIfPresent(String icao) {
        String key = "metar:" + icao;
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                String raw = jedis.get(key);
                if (raw != null) return objectMapper.readValue(raw, CachedMetarData.class);
            } catch (Exception ignored) {}
        }
        return memoryCache.get(key);
    }

    /** Retorna todos os METARs do hash global em uma única operação atômica. */
    public static java.util.Map<String, CachedMetarData> getAllCached() {
        java.util.Map<String, CachedMetarData> result = new java.util.LinkedHashMap<>();
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                java.util.Map<String, String> all = jedis.hgetAll("metar:all");
                // se Redis retornou vazio, aguarda 300ms e tenta mais uma vez
                if (all.isEmpty()) {
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    all = jedis.hgetAll("metar:all");
                }
                for (java.util.Map.Entry<String, String> e : all.entrySet()) {
                    try { result.put(e.getKey(), objectMapper.readValue(e.getValue(), CachedMetarData.class)); }
                    catch (Exception ignored) {}
                }
                return result;
            } catch (Exception ignored) {}
        }
        // fallback memória
        for (java.util.Map.Entry<String, CachedMetarData> e : memoryCache.entrySet()) {
            result.put(e.getKey().replace("metar:", ""), e.getValue());
        }
        return result;
    }

    public static String getCacheStats() {
        int localEntries = memoryCache.size();
        String redisStatus = jedisPool != null ? "conectado" : "desconectado";
        return String.format("Redis: %s, Local: %d entradas", redisStatus, localEntries);
    }

    public static Jedis getJedis() {
        if (jedisPool == null) return null;
        try { return jedisPool.getResource(); } catch (Exception e) { return null; }
    }
}
