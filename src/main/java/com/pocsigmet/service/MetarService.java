package com.pocsigmet.service;

import com.pocsigmet.RedemetMetarClient;
import com.pocsigmet.RedemetSigmetClient;
import com.pocsigmet.RedisMetarCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class MetarService {

    private static final Logger log = LoggerFactory.getLogger(MetarService.class);

    private final RedemetMetarClient METAR_CLIENT;
    private final RedemetSigmetClient SIGMET_CLIENT;

    private final ExecutorService metarExecutor;
    private final java.util.function.Supplier<List<AirportData>> airportsSupplier;

    /** Construtor Spring — injetado pelo container */
    @org.springframework.beans.factory.annotation.Autowired
    public MetarService(RedemetMetarClient metarClient, RedemetSigmetClient sigmetClient) {
        this.METAR_CLIENT = metarClient;
        this.SIGMET_CLIENT = sigmetClient;
        this.metarExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        this.airportsSupplier = () -> com.pocsigmet.AirportConfig.getAirports().stream()
            .map(a -> new AirportData((String)a[0], (String)a[1], (Double)a[2], (Double)a[3]))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Dados básicos de aeroporto (DTO público).
     */
    public static class AirportData {
        public final String icao, nome;
        public final double lat, lon;
        
        public AirportData(String icao, String nome, double lat, double lon) {
            this.icao = icao; this.nome = nome; this.lat = lat; this.lon = lon;
        }
    }

    /**
     * Resultado de METAR formatado como JSON.
     */
    public static class MetarJsonResult {
        public final String json;
        public final boolean success;
        
        public MetarJsonResult(String json, boolean success) {
            this.json = json; this.success = success;
        }
    }

    // ============= Helpers =============

    private static final java.util.regex.Pattern METAR_TIME_PAT =
        java.util.regex.Pattern.compile("\\b(\\d{2})(\\d{2})(\\d{2})Z\\b");

    private boolean isOutdated(String metarText) {
        if (metarText == null) return false;
        java.util.regex.Matcher m = METAR_TIME_PAT.matcher(metarText);
        if (!m.find()) return false;
        try {
            int day  = Integer.parseInt(m.group(1));
            int hour = Integer.parseInt(m.group(2));
            int min  = Integer.parseInt(m.group(3));
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
            java.time.ZonedDateTime mt  = now.withDayOfMonth(day).withHour(hour).withMinute(min).withSecond(0);
            // virada de mês: só subtrai se estiver mais de 30min no futuro (evita falso positivo por clock skew)
            if (mt.isAfter(now) && java.time.Duration.between(now, mt).toMinutes() > 30) mt = mt.minusMonths(1);
            return java.time.Duration.between(mt, now).toMinutes() > 60;
        } catch (Exception e) { return false; }
    }
    
    private String conditionToColor(String condition) {
        switch (condition) {
            case "VFR": return "#00FF00";
            case "MVFR": return "#FFFF00";
            case "IFR": return "#FF0000";
            case "LIFR": return "#FF00FF";
            default: return "#808080";
        }
    }

    // ============= Endpoints =============

    /**
     * METAR para SBSP via Redis Cache.
     */
    public String getMetarSbsp() {
        try {
            RedisMetarCacheService.CachedMetarData cachedData = RedisMetarCacheService.getCachedMetar("SBSP");
            String color = conditionToColor(cachedData.condition);
            
            return String.format("{\"icao\":\"SBSP\",\"condition\":\"%s\",\"color\":\"%s\",\"lat\":-23.6266,\"lon\":-46.6556,\"metarText\":\"%s\",\"hasAviso\":%s}", 
                cachedData.condition, color, 
                cachedData.metarText.replace("\"", "\\\"").replace("\n", "\\n"), 
                cachedData.hasAviso);
        } catch (Exception e) {
            return "{\"icao\":\"SBSP\",\"condition\":\"ERROR\",\"color\":\"#808080\",\"lat\":-23.6266,\"lon\":-46.6556,\"metarText\":\"Erro ao obter METAR/SPECI\",\"hasAviso\":false}";
        }
    }

    /**
     * METAR para SBOI via RedemetMetarClient direto.
     */
    public String getMetarSboi() {
        try {
            String condition = METAR_CLIENT.getMetarCondition("SBOI");
            String latestMessage = METAR_CLIENT.getLatestMetarOrSpeci("SBOI");
            boolean hasAviso = METAR_CLIENT.hasAvisoForAirport("SBOI");
            
            String finalText = latestMessage;
            if (hasAviso) {
                String avisoText = METAR_CLIENT.getAvisoTextForAirport("SBOI");
                if (!avisoText.isEmpty()) {
                    finalText += "\n\n⚠️ AVISO: " + avisoText;
                }
            }
            
            String color = conditionToColor(condition);
            
            return String.format("{\"icao\":\"SBOI\",\"condition\":\"%s\",\"color\":\"%s\",\"lat\":2.8461,\"lon\":-60.6903,\"metarText\":\"%s\",\"hasAviso\":%s}", 
                condition, color, finalText.replace("\"", "\\\"").replace("\n", "\\n"), hasAviso);
        } catch (Exception e) {
            return "{\"icao\":\"SBOI\",\"condition\":\"ERROR\",\"color\":\"#808080\",\"lat\":2.8461,\"lon\":-60.6903,\"metarText\":\"Erro ao obter METAR/SPECI\",\"hasAviso\":false}";
        }
    }

    /**
     * METAR para todos os aeroportos principais (sequencial).
     */
    public String getMetarAll() {
        String[][] airports = {
            {"SBGR", "-23.432", "-46.469"}, {"SBSP", "-23.626", "-46.656"},
            {"SBRJ", "-22.910", "-43.163"}, {"SBGL", "-22.809", "-43.250"},
            {"SBCF", "-19.624", "-43.971"}, {"SBSV", "-12.908", "-38.322"},
            {"SBRF", "-8.126", "-34.923"},  {"SBCY", "-15.653", "-56.117"},
            {"SBCT", "-25.528", "-49.176"}, {"SBPA", "-29.994", "-51.171"},
            {"SBGO", "-16.632", "-49.221"}, {"SBKP", "-23.007", "-47.134"},
            {"SBBE", "-1.379", "-48.476"},  {"SBMQ", "0.050", "-51.072"},
            {"SBSL", "-2.585", "-44.234"},  {"SBFZ", "-3.776", "-38.533"},
            {"SBMO", "-9.511", "-35.792"},  {"SBCG", "-20.469", "-54.672"},
            {"SBJP", "-7.148", "-34.948"},  {"SBVT", "-20.258", "-40.286"},
            {"SBEG", "-3.038", "-60.049"},  {"SBOI", "2.846", "-60.690"},
            {"SBMN", "-3.146", "-59.986"},  {"SBBR", "-15.871", "-47.919"},
            {"SBAX", "-19.634", "-43.969"}
        };
        
        try {
            StringBuilder jsonBuilder = new StringBuilder("[");
            
            for (int i = 0; i < airports.length; i++) {
                String icao = airports[i][0];
                String lat = airports[i][1];
                String lon = airports[i][2];
                
                try {
                    String condition = METAR_CLIENT.getMetarCondition(icao);
                    String latestMessage = METAR_CLIENT.getLatestMetarOrSpeci(icao);
                    boolean hasAviso = METAR_CLIENT.hasAvisoForAirport(icao);
                    
                    String finalText = latestMessage;
                    if (hasAviso) {
                        String avisoText = METAR_CLIENT.getAvisoTextForAirport(icao);
                        if (!avisoText.isEmpty()) {
                            finalText += "\n\n⚠️ AVISO: " + avisoText;
                        }
                    }
                    
                    // Buscar TAF
                    String tafText = "TAF não disponível";
                    try {
                        String tafData = SIGMET_CLIENT.getTafForAirport(icao);
                        if (tafData != null && !tafData.trim().isEmpty()) {
                            tafText = tafData.trim();
                        }
                    } catch (Exception e) {
                        log.warn("Erro ao buscar TAF para {}: {}", icao, e.getMessage());
                    }
                    
                    String color = conditionToColor(condition);
                    
                    if (i > 0) jsonBuilder.append(",");
                    jsonBuilder.append(String.format(
                        "{\"icao\":\"%s\",\"condition\":\"%s\",\"color\":\"%s\",\"lat\":%s,\"lon\":%s,\"metarText\":\"%s\",\"tafText\":\"%s\",\"hasAviso\":%s}",
                        icao, condition, color, lat, lon, 
                        finalText.replace("\"", "\\\"").replace("\n", "\\n"),
                        tafText.replace("\"", "\\\"").replace("\n", "\\n"), hasAviso
                    ));
                } catch (Exception e) {
                    if (i > 0) jsonBuilder.append(",");
                    jsonBuilder.append(String.format(
                        "{\"icao\":\"%s\",\"condition\":\"ERROR\",\"color\":\"#808080\",\"lat\":%s,\"lon\":%s,\"metarText\":\"Erro\",\"hasAviso\":false}",
                        icao, lat, lon
                    ));
                }
            }
            
            jsonBuilder.append("]");
            return jsonBuilder.toString();
        } catch (Exception e) {
            return "[{\"icao\":\"ERROR\",\"condition\":\"ERROR\",\"color\":\"#808080\",\"lat\":0,\"lon\":0,\"metarText\":\"Erro geral\",\"hasAviso\":false}]";
        }
    }

    /**
     * METAR para Top N aeroportos SB (paralelo com Redis cache).
     */
    public String getMetarTopSB(int limit) {
        try {
            log.info("Buscando Top {} SB com REDIS CACHE...", limit);
            long startTime = System.currentTimeMillis();            
            List<AirportData> airports = airportsSupplier.get();
            if (airports == null || airports.isEmpty()) {
                return "[]";
            }
            List<AirportData> limited = airports.subList(0, Math.min(limit, airports.size()));
            
            List<CompletableFuture<String>> futures = limited.stream()
                .map(airport -> CompletableFuture.supplyAsync(() -> {
                    try {
                        RedisMetarCacheService.CachedMetarData cachedData = RedisMetarCacheService.getCachedMetar(airport.icao);
                        
                        String finalCondition = cachedData.condition;
                        String color;
                        if (cachedData.metarText.contains("não disponível")) {
                            finalCondition = "UNKNOWN";
                            color = "#808080";
                        } else {
                            color = isOutdated(cachedData.metarText) ? "#8B4513" : conditionToColor(cachedData.condition);
                        }

                        return String.format(
                            "{\"icao\":\"%s\",\"nome\":\"%s\",\"condition\":\"%s\",\"color\":\"%s\",\"lat\":%s,\"lon\":%s,\"metarText\":\"%s\",\"tafText\":\"%s\",\"hasAviso\":%s,\"isSpeci\":%s}",
                            airport.icao, airport.nome, finalCondition, color,
                            airport.lat, airport.lon,
                            cachedData.metarText.replace("\"", "\\\"").replace("\n", "\\n"),
                            cachedData.tafText.replace("\"", "\\\"").replace("\n", "\\n"),
                            cachedData.hasAviso,
                            cachedData.metarText.startsWith("SPECI")
                        );
                    } catch (Exception e) {
                        return String.format(
                            "{\"icao\":\"%s\",\"nome\":\"%s\",\"condition\":\"ERROR\",\"color\":\"#808080\",\"lat\":%s,\"lon\":%s,\"metarText\":\"Erro METAR\",\"tafText\":\"\",\"hasAviso\":false,\"isSpeci\":false}",
                            airport.icao, airport.nome, airport.lat, airport.lon
                        );
                    }
                }, metarExecutor))
                .collect(Collectors.toList());
            
            // Aguardar com timeout de 15s
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            try {
                allFutures.get(15, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("Timeout após 15s - usando resultados parciais");
            }
            
            StringBuilder jsonBuilder = new StringBuilder("[");
            boolean first = true;
            for (int i = 0; i < futures.size(); i++) {
                CompletableFuture<String> future = futures.get(i);
                AirportData airport = limited.get(i);
                try {
                    String result = future.getNow(null);
                    if (result == null) {
                        // Timeout — usar cache stale se disponível, senão UNKNOWN
                        RedisMetarCacheService.CachedMetarData stale = RedisMetarCacheService.getStaleMetar(airport.icao);
                        result = stale != null
                            ? String.format("{\"icao\":\"%s\",\"nome\":\"%s\",\"condition\":\"%s\",\"color\":\"%s\",\"lat\":%s,\"lon\":%s,\"metarText\":\"%s\",\"tafText\":\"%s\",\"hasAviso\":%s,\"isSpeci\":false}",
                                airport.icao, airport.nome, stale.condition,
                                isOutdated(stale.metarText) ? "#8B4513" : conditionToColor(stale.condition),
                                airport.lat, airport.lon,
                                stale.metarText.replace("\"", "\\\"").replace("\n", "\\n"),
                                stale.tafText.replace("\"", "\\\"").replace("\n", "\\n"),
                                stale.hasAviso)
                            : String.format("{\"icao\":\"%s\",\"nome\":\"%s\",\"condition\":\"UNKNOWN\",\"color\":\"#808080\",\"lat\":%s,\"lon\":%s,\"metarText\":\"Aguardando dados...\",\"tafText\":\"\",\"hasAviso\":false,\"isSpeci\":false}",
                                airport.icao, airport.nome, airport.lat, airport.lon);
                    }
                    if (!first) jsonBuilder.append(",");
                    jsonBuilder.append(result);
                    first = false;
                } catch (Exception e) {
                    // ignorar
                }
            }
            jsonBuilder.append("]");
            
            long endTime = System.currentTimeMillis();
            log.info("Top {} SB retornado em {}ms", limit, (endTime - startTime));
            log.info("{}", RedisMetarCacheService.getCacheStats());
            
            return jsonBuilder.toString();
        } catch (Exception e) {
            return "[{\"icao\":\"ERROR\",\"condition\":\"ERROR\",\"color\":\"#808080\",\"lat\":0,\"lon\":0,\"metarText\":\"Erro geral\",\"hasAviso\":false}]";
        }
    }

    private static final java.util.regex.Pattern ALERT_PATTERN =
        java.util.regex.Pattern.compile("(?:[-+]?\\b(TSRA|TSGR|TSGS|FZFG|BCFG|MIFG|PRFG|SHRA|VCSH|RERA|RERASN|TS|GR|WS|RA|FG|BR)\\b|\\b(?:FEW|SCT|BKN|OVC)\\d{3}(CB|TCU)\\b)");
    private static final java.util.regex.Pattern GUST_PATTERN =
        java.util.regex.Pattern.compile("\\d{3}\\d{2}G(\\d{2,3})KT");
    private static final java.util.regex.Pattern METAR_TIME_PATTERN =
        java.util.regex.Pattern.compile("\\b(\\d{6}Z)\\b");

    public String getMetarAlerts() {
        // leitura atômica via hash global — sem jedis.keys()
        java.util.Map<String, RedisMetarCacheService.CachedMetarData> all =
            RedisMetarCacheService.getAllCached();

        java.util.List<String> alerts = new java.util.ArrayList<>();
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);

        for (java.util.Map.Entry<String, RedisMetarCacheService.CachedMetarData> entry : all.entrySet()) {
            String icao = entry.getKey();
            RedisMetarCacheService.CachedMetarData data = entry.getValue();

            if (data.metarText == null || data.metarText.contains("não disponível")) continue;

            // linha METAR sem aviso
            String metarOnly = data.metarText.split("\n")[0].trim();

            // extrair timestamp do METAR
            java.util.regex.Matcher tm = METAR_TIME_PATTERN.matcher(metarOnly);
            if (!tm.find()) continue;
            String metarTime = tm.group(1);

            // descartar METARs com mais de 2h
            try {
                int day  = Integer.parseInt(metarTime.substring(0, 2));
                int hour = Integer.parseInt(metarTime.substring(2, 4));
                int min  = Integer.parseInt(metarTime.substring(4, 6));
                java.time.ZonedDateTime obs = now
                    .withDayOfMonth(day).withHour(hour).withMinute(min).withSecond(0).withNano(0);
                if (obs.isAfter(now)) obs = obs.minusMonths(1);
                if (java.time.Duration.between(obs, now).toMinutes() > 120) continue;
            } catch (Exception ignored) { continue; }

            // detectar fenômenos
            java.util.LinkedHashSet<String> found = new java.util.LinkedHashSet<>();
            java.util.regex.Matcher mm = ALERT_PATTERN.matcher(metarOnly);
            while (mm.find()) {
                String g = mm.group(1) != null ? mm.group(1) : mm.group(2);
                if (g != null) found.add(g);
            }
            java.util.regex.Matcher gm = GUST_PATTERN.matcher(metarOnly);
            if (gm.find()) { int g = Integer.parseInt(gm.group(1)); if (g > 20) found.add("G" + g + "KT"); }
            if (found.isEmpty()) continue;

            String phenomena = String.join(",", found);
            String msgEscaped = metarOnly.replace("\"", "\\\"");
            alerts.add(String.format(
                "{\"icao\":\"%s\",\"metarTime\":\"%s\",\"phenomena\":\"%s\",\"metarText\":\"%s\"}",
                icao, metarTime, phenomena, msgEscaped));
        }

        // ordenar por timestamp decrescente (mais recente primeiro)
        alerts.sort((a, b) -> {
            String ta = a.replaceAll(".*\"metarTime\":\"(\\d{6}Z)\".*", "$1");
            String tb = b.replaceAll(".*\"metarTime\":\"(\\d{6}Z)\".*", "$1");
            return tb.compareTo(ta);
        });

        return "[" + String.join(",", alerts) + "]";
    }
}
