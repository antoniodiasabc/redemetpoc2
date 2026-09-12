package com.pocsigmet.grib2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;
import ucar.ma2.Array;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import redis.clients.jedis.Jedis;

@Service
public class WindBarbService {
    private static final Logger log = LoggerFactory.getLogger(WindBarbService.class);
    
    @Autowired
    private Grib2Downloader downloader;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, Integer> LEVEL_MAPPING = Map.of(
        "surface", 0,
        "fl050", 85000,
        "fl100", 70000,
        "fl180", 50000,
        "fl240", 40000,
        "fl300", 30000,
        "fl390", 25000,
        "fl450", 20000,
        "fl530", 15000
    );

    private static final Map<String, List<WindBarbData>> memCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> memCacheTime = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 3600_000L; // 1 hora

    public List<WindBarbData> getWindBarbs(String level) throws Exception {
        return getWindBarbs(level, 4);
    }
    
    public List<WindBarbData> getWindBarbs(String level, int skip) throws Exception {
        // 1. Cache em memória (por instância, mais rápido)
        Long lastTime = memCacheTime.get(level);
        if (lastTime != null && (System.currentTimeMillis() - lastTime) < CACHE_DURATION) {
            List<WindBarbData> cached = memCache.get(level);
            if (cached != null) {
                log.info(String.valueOf("⚡ Cache memória vento: " + level));
                return applySampling(cached, skip);
            }
        }

        // 2. Redis (compartilhado entre containers)
        List<WindBarbData> allData = loadFromRedis(level);

        // 3. Arquivo em disco (fallback — sem verificação de idade, usa o que tiver)
        if (allData == null) {
            String cacheFile = "data/wind_cache_" + level + ".json";
            File cache = new File(cacheFile);
            if (cache.exists()) {
                log.info(String.valueOf("🚀 Usando cache arquivo vento: " + cacheFile));
                allData = loadFromFile(cacheFile);
                if (allData != null) saveToRedis(level, allData);
            }
        }

        // 4. Sem cache disponível — NÃO extrai GRIB2 durante request HTTP
        if (allData == null) {
            throw new RuntimeException("Cache de vento não disponível para " + level + ". Aguarde o próximo ciclo de extração.");
        }

        // Popular cache em memória
        memCache.put(level, allData);
        memCacheTime.put(level, System.currentTimeMillis());

        return applySampling(allData, skip);
    }

    // Chamado apenas pelo scheduler — pode demorar
    public List<WindBarbData> extractAndCache(String gribFile, String level) throws Exception {
        List<WindBarbData> allData = extractWindData(gribFile, level, 1);

        // Salvar no arquivo
        String cacheFile = "data/wind_cache_" + level + ".json";
        saveToFile(allData, cacheFile);

        // Salvar no Redis
        saveToRedis(level, allData);

        // Atualizar cache em memória
        memCache.put(level, allData);
        memCacheTime.put(level, System.currentTimeMillis());

        log.info("💾 Cache atualizado: " + level + " (" + allData.size() + " pontos)");
        return allData;
    }

    private List<WindBarbData> loadFromRedis(String level) {
        try (Jedis jedis = com.pocsigmet.RedisMetarCacheService.getJedis()) {
            if (jedis == null) return null;
            String json = jedis.get("wind:" + level);
            if (json == null) return null;
            log.info(String.valueOf("⚡ Cache Redis vento: " + level));
            return objectMapper.readValue(json, new TypeReference<List<WindBarbData>>() {});
        } catch (Exception e) {
            log.warn(String.valueOf("Redis wind read error: " + e.getMessage()));
            return null;
        }
    }

    private void saveToRedis(String level, List<WindBarbData> data) {
        try (Jedis jedis = com.pocsigmet.RedisMetarCacheService.getJedis()) {
            if (jedis == null) return;
            String json = objectMapper.writeValueAsString(data);
            jedis.setex("wind:" + level, 3600, json);
            log.info(String.valueOf("✅ Redis wind salvo: " + level));
        } catch (Exception e) {
            log.warn(String.valueOf("Redis wind save error: " + e.getMessage()));
        }
    }

    private List<WindBarbData> loadFromFile(String cacheFile) {
        try {
            return objectMapper.readValue(new File(cacheFile), new TypeReference<List<WindBarbData>>() {});
        } catch (Exception e) {
            log.warn(String.valueOf("❌ Erro carregando cache arquivo: " + e.getMessage()));
            return null;
        }
    }

    private void saveToFile(List<WindBarbData> data, String cacheFile) {
        try {
            objectMapper.writeValue(new File(cacheFile), data);
        } catch (Exception e) {
            log.warn(String.valueOf("❌ Erro salvando cache arquivo: " + e.getMessage()));
        }
    }
    
    private List<WindBarbData> extractWindData(String gribFile, String level) throws Exception {
        return extractWindData(gribFile, level, 4);
    }
    
    private List<WindBarbData> extractWindData(String gribFile, String level, int skip) throws Exception {
        List<WindBarbData> windBarbs = new ArrayList<>();
        
        try (NetcdfFile ncfile = NetcdfFiles.open(gribFile)) {
            // Encontrar variáveis U/V
            Variable uWind = null, vWind = null;
            
            if (level.equals("surface")) {
                uWind = ncfile.findVariable("u-component_of_wind_height_above_ground");
                vWind = ncfile.findVariable("v-component_of_wind_height_above_ground");
            } else {
                uWind = ncfile.findVariable("u-component_of_wind_isobaric");
                vWind = ncfile.findVariable("v-component_of_wind_isobaric");
            }
            
            if (uWind == null || vWind == null) {
                throw new RuntimeException("Variáveis de vento não encontradas para " + level);
            }
            
            // Obter coordenadas
            Variable latVar = ncfile.findVariable("lat");
            Variable lonVar = ncfile.findVariable("lon");
            
            Array latData = latVar.read();
            Array lonData = lonVar.read();
            
            float[] latArray = (float[]) latData.copyTo1DJavaArray();
            float[] lonArray = (float[]) lonData.copyTo1DJavaArray();
            
            // Determinar índice do nível
            int levelIndex = getLevelIndex(ncfile, level);

            // Ler arrays completos de uma vez (muito mais eficiente que leitura ponto a ponto)
            Array uFull, vFull;
            if (level.equals("surface")) {
                uFull = uWind.read();
                vFull = vWind.read();
            } else {
                int[] origin = {0, levelIndex, 0, 0};
                int[] shape  = {1, 1, latArray.length, lonArray.length};
                uFull = uWind.read(origin, shape);
                vFull = vWind.read(origin, shape);
            }

            // Extrair dados com amostragem dinâmica baseada no skip
            for (int i = 0; i < latArray.length; i += skip) {
                for (int j = 0; j < lonArray.length; j += skip) {
                    double lat = latArray[i];
                    double lon = lonArray[j];

                    // Filtrar América do Sul
                    if (isInSouthAmerica(lat, lon)) {
                        int idx = i * lonArray.length + j;
                        float u = uFull.getFloat(idx);
                        float v = vFull.getFloat(idx);

                        if (!Float.isNaN(u) && !Float.isNaN(v)) {
                            windBarbs.add(calculateWindBarb(u, v, lat, lon, level));
                        }
                    }
                }
            }
        }
        
        return windBarbs;
    }
    
    // Aplicar amostragem nos dados em memória
    private List<WindBarbData> applySampling(List<WindBarbData> allData, int skip) {
        List<WindBarbData> sampled = new ArrayList<>();
        
        // Organizar dados por lat/lon para aplicar skip geográfico
        Map<String, WindBarbData> gridMap = new HashMap<>();
        for (WindBarbData data : allData) {
            String key = String.format("%.1f_%.1f", data.getLat(), data.getLon());
            gridMap.put(key, data);
        }
        
        // Aplicar skip baseado em coordenadas
        for (WindBarbData data : allData) {
            double lat = data.getLat();
            double lon = data.getLon();
            
            // Verificar se deve incluir baseado no skip
            int latIndex = (int) Math.round((lat + 90) * 2); // Converter para índice
            int lonIndex = (int) Math.round((lon + 180) * 2);
            
            if (latIndex % skip == 0 && lonIndex % skip == 0) {
                sampled.add(data);
            }
        }
        
        return sampled;
    }
    
    private int getLevelIndex(NetcdfFile ncfile, String level) throws Exception {
        if (level.equals("surface")) return 0;
        
        Variable isobaric = ncfile.findVariable("isobaric");
        if (isobaric == null) throw new RuntimeException("Níveis isobáricos não encontrados");
        
        Array isoData = isobaric.read();
        float[] levels = (float[]) isoData.copyTo1DJavaArray();
        
        int targetPressure = LEVEL_MAPPING.get(level);
        
        for (int i = 0; i < levels.length; i++) {
            if (Math.abs(levels[i] - targetPressure) < 500) {
                return i;
            }
        }
        
        throw new RuntimeException("Nível " + level + " não encontrado");
    }
    
    private boolean isInSouthAmerica(double lat, double lon) {
        return lat >= -40.0 && lat <= 15.0 && 
               ((lon >= -85.0 && lon <= -10.0) || (lon >= 275.0 && lon <= 350.0));
    }
    
    private WindBarbData calculateWindBarb(float u, float v, double lat, double lon, String level) {
        double speed = Math.sqrt(u*u + v*v) * 1.94384; // m/s para nós
        double direction = Math.atan2(-u, -v) * 180.0 / Math.PI;
        if (direction < 0) direction += 360;
        
        WindBarbData barb = new WindBarbData();
        barb.setLat(lat);
        barb.setLon(lon);
        barb.setU(u);
        barb.setV(v);
        barb.setSpeed(speed);
        barb.setDirection(direction);
        barb.setLevel(level);
        
        return barb;
    }
}
