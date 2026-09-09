package com.pocsigmet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.*;
import java.util.Collections;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@org.springframework.stereotype.Component
public class RedemetSigmetClient {
    private static final Logger log = LoggerFactory.getLogger(RedemetSigmetClient.class);
    private static final String BASE_URL = "https://opmet.decea.mil.br/redemet/consulta_redemet";
    @org.springframework.beans.factory.annotation.Value("${app.redemet.username:redemetwebservice}")
    private String username;
    @org.springframework.beans.factory.annotation.Value("${app.redemet.password:Mudar12345@}")
    private String password;
    private static final List<String> FIRS = Arrays.asList("SBAZ", "SBAO", "SBBS", "SBCW", "SBRE");
    private static final String REDIS_TOKEN_KEY = "redemet_token";

    private final HttpClient client;
    private final ObjectMapper mapper;
    private static JedisPool jedisPool;

    // Token local como fallback se Redis falhar
    private static String sharedToken;
    private static long tokenExpiry = 0;
    private static final Object tokenLock = new Object();

    public RedemetSigmetClient() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.mapper = new ObjectMapper();
        if (jedisPool == null) {
            try {
                jedisPool = new JedisPool(System.getenv().getOrDefault("REDIS_HOST", "redis"), 6379);
            } catch (Exception e) {
                log.warn(String.valueOf("⚠️ Redis não disponível para token: " + e.getMessage()));
            }
        }
    }

    private String getTokenFromRedis() {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(REDIS_TOKEN_KEY);
        } catch (Exception e) { return null; }
    }

    private void saveTokenToRedis(String token) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(REDIS_TOKEN_KEY, 3000, token); // 50 min
        } catch (Exception e) {
            log.warn(String.valueOf("⚠️ Erro ao salvar token no Redis: " + e.getMessage()));
        }
    }

    public String authenticate() throws IOException, InterruptedException {
        synchronized (tokenLock) {
            // 1. Tentar Redis primeiro
            if (jedisPool != null) {
                String redisToken = getTokenFromRedis();
                if (redisToken != null && !redisToken.isEmpty()) {
                    sharedToken = redisToken;
                    tokenExpiry = System.currentTimeMillis() + (50 * 60 * 1000);
                    return sharedToken;
                }
            }

            // 2. Fallback: token local ainda válido
            if (sharedToken != null && System.currentTimeMillis() < tokenExpiry) {
                return sharedToken;
            }

            // 3. Autenticar e salvar no Redis
            String loginData = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://opmet.decea.mil.br/adm/login"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginData))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode jsonResponse = mapper.readTree(response.body());
                String authHeader = jsonResponse.get("authorization").asText();
                sharedToken = authHeader.replace("Bearer ", "");
                tokenExpiry = System.currentTimeMillis() + (50 * 60 * 1000);
                saveTokenToRedis(sharedToken);
                return sharedToken;
            }

            throw new RuntimeException("Falha na autenticação: " + response.statusCode());
        }
    }
    
    public String getSigmetForFir(String fir) throws IOException, InterruptedException {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime sixHoursAgo = now.minusHours(6);  // ALTERADO: -6h ao invés de -3h
        LocalDateTime sixHoursLater = now.plusHours(6);
        
        String dataIni = String.format("%04d%02d%02d%02d", sixHoursAgo.getYear(), sixHoursAgo.getMonthValue(), sixHoursAgo.getDayOfMonth(), sixHoursAgo.getHour());
        String dataFim = String.format("%04d%02d%02d%02d", sixHoursLater.getYear(), sixHoursLater.getMonthValue(), sixHoursLater.getDayOfMonth(), sixHoursLater.getHour());
        
        log.info("🔍 Buscando SIGMETs para " + fir + " de " + dataIni + " até " + dataFim + " (-6h até +6h)");  // ALTERADO: log atualizado
        
        String endpoint = String.format("?local=%s&msg=SIGMET&data_ini=%s&data_fim=%s&data_hora=nao", 
                                       fir, dataIni, dataFim);
        
        log.info(String.valueOf("🔍 URL completa: " + BASE_URL + endpoint));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + endpoint))
            .header("Authorization", "Bearer " + sharedToken)
            .header("Accept", "text/plain")
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(30))
            .build();
            
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        log.info(String.valueOf("🔍 Status resposta " + fir + ": " + response.statusCode()));

        // Se 403, token expirou — invalidar Redis e reautenticar
        if (response.statusCode() == 403) {
            synchronized (tokenLock) { sharedToken = null; tokenExpiry = 0; }
            if (jedisPool != null) { try (Jedis j = jedisPool.getResource()) { j.del(REDIS_TOKEN_KEY); } catch (Exception ignored) {} }
            authenticate();
            return getSigmetForFir(fir);
        }
        
        if (response.statusCode() == 200) {
            String body = response.body();
            System.out.println("🔍 Resposta " + fir + " (primeiros 200 chars): " + 
                             (body.length() > 200 ? body.substring(0, 200) + "..." : body));
            return body;
        }
        return "";
    }
    
    public String getSigmets() throws IOException, InterruptedException {
        if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) {
            authenticate();
        }
        
        StringBuilder allSigmets = new StringBuilder();
        for (String fir : FIRS) {
            String sigmetData = getSigmetForFir(fir);
            if (!sigmetData.isEmpty()) {
                allSigmets.append(sigmetData).append("\n");
            }
        }
        return allSigmets.toString();
    }
    
    public String getSigmetsJson() throws IOException, InterruptedException {
        if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) {
            authenticate();
        }
        
        List<Map<String, Object>> allSigmets = new ArrayList<>();
        
        for (String fir : FIRS) {
            String sigmetData = getSigmetForFir(fir);
            if (!sigmetData.isEmpty()) {
                log.info("🔍 " + fir + " retornou " + sigmetData.split("=").length + " mensagens");
                
                // Identificar SIGMETs cancelados
                Set<String> cancelledNumbers = new HashSet<>();
                String[] messages = sigmetData.split("=");
                for (String message : messages) {
                    if (message.contains("CNL SIGMET")) {
                        Pattern cnlPattern = Pattern.compile("CNL SIGMET (\\d+)");
                        Matcher cnlMatcher = cnlPattern.matcher(message);
                        if (cnlMatcher.find()) {
                            cancelledNumbers.add(cnlMatcher.group(1));
                            log.info("🔍 " + fir + " SIGMET " + cnlMatcher.group(1) + " cancelado");
                        }
                    }
                }
                
                // Processar SIGMETs válidos
                for (String message : messages) {
                    if (message.contains("SIGMET") && message.contains(fir) && message.contains("WI ")) {
                        // Extrair número do SIGMET
                        Pattern numberPattern = Pattern.compile(fir + "\\s+SIGMET\\s+(\\d+)");
                        Matcher numberMatcher = numberPattern.matcher(message);
                        if (numberMatcher.find()) {
                            String sigmetNumber = numberMatcher.group(1);
                            
                            // Verificar se foi cancelado
                            if (cancelledNumbers.contains(sigmetNumber)) {
                                log.info("❌ " + fir + " SIGMET " + sigmetNumber + " cancelado - ignorando");
                                continue;
                            }
                        }
                        
                        log.info(String.valueOf("🔍 Processando SIGMET: " + message.substring(0, Math.min(100, message.length()))));
                        Map<String, Object> sigmet = parseSigmetCoords(message.trim(), fir);
                        if (sigmet != null) {
                            // Filtrar apenas SIGMETs válidos
                            String validPeriod = (String) sigmet.get("validPeriod");
                            log.info(String.valueOf("🔍 DEBUG RECUPERAÇÃO: validPeriod = " + validPeriod));
                            log.info(String.valueOf("🔍 DEBUG SIGMET KEYS: " + sigmet.keySet()));
                            if (sigmet.get("properties") != null) {
                                Map<String, Object> props = (Map<String, Object>) sigmet.get("properties");
                                log.info("🔍 DEBUG PROPERTIES: validPeriod = " + props.get("validPeriod"));
                                validPeriod = (String) props.get("validPeriod");
                            }
                            if (isSigmetValid(validPeriod)) {
                                log.info(String.valueOf("✅ SIGMET válido adicionado: " + fir + " SIGMET " + numberMatcher.group(1)));
                                allSigmets.add(sigmet);
                            } else {
                                log.info(String.valueOf("⏰ SIGMET expirado ignorado: " + fir + " SIGMET " + numberMatcher.group(1)));
                            }
                        } else {
                            log.info(String.valueOf("❌ SIGMET rejeitado no parsing: " + fir + " SIGMET " + numberMatcher.group(1)));
                        }
                    }
                }
            }
        }
        
        return mapper.writeValueAsString(allSigmets);
    }
    
    private boolean isSigmetValid(String validPeriod) {
        if (validPeriod == null || !validPeriod.contains("/")) {
            log.info(String.valueOf("🔍 DEBUG: validPeriod inválido: " + validPeriod));
            return false;
        }
        
        String[] periodo = validPeriod.split("/");
        String dataInicio = periodo[0]; // "271530"
        String dataFim = periodo[1];    // "271930"
        
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String agoraUTC = String.format("%02d%02d%02d", 
            now.getDayOfMonth(), now.getHour(), now.getMinute());
        
        boolean inicioOk = dataInicio.compareTo(agoraUTC) <= 0;
        boolean fimOk = dataFim.compareTo(agoraUTC) > 0;
        boolean valido = inicioOk && fimOk;
        
        log.info(String.valueOf("🔍 DEBUG SIGMET: " + validPeriod));
        log.info(String.valueOf("  Agora: " + agoraUTC));
        log.info(String.valueOf("  Início: " + dataInicio + " <= " + agoraUTC + " ? " + inicioOk));
        log.info(String.valueOf("  Fim: " + dataFim + " > " + agoraUTC + " ? " + fimOk));
        log.info("  RESULTADO: " + (valido ? "✅ VÁLIDO" : "❌ INVÁLIDO"));
        
        return valido;
    }
    
    public String getSigmetsAllJson() throws IOException, InterruptedException {
        if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) {
            authenticate();
        }
        
        List<Map<String, Object>> allSigmets = new ArrayList<>();
        
        for (String fir : FIRS) {
            String sigmetData = getSigmetForFir(fir);
            if (!sigmetData.isEmpty()) {
                Set<String> cancelledNumbers = new HashSet<>();
                String[] messages = sigmetData.split("=");
                for (String message : messages) {
                    if (message.contains("CNL SIGMET")) {
                        Pattern cnlPattern = Pattern.compile("CNL SIGMET (\\d+)");
                        Matcher cnlMatcher = cnlPattern.matcher(message);
                        if (cnlMatcher.find()) {
                            cancelledNumbers.add(cnlMatcher.group(1));
                        }
                    }
                }
                
                for (String message : messages) {
                    if (message.contains("SIGMET") && message.contains(fir) && message.contains("WI ")) {
                        Pattern numberPattern = Pattern.compile(fir + "\\s+SIGMET\\s+(\\d+)");
                        Matcher numberMatcher = numberPattern.matcher(message);
                        if (numberMatcher.find()) {
                            String sigmetNumber = numberMatcher.group(1);
                            
                            if (cancelledNumbers.contains(sigmetNumber)) {
                                continue;
                            }
                        }
                        
                        Map<String, Object> sigmet = parseSigmetCoords(message.trim(), fir);
                        if (sigmet != null) {
                            allSigmets.add(sigmet); // SEM FILTRO DE VALIDADE
                        }
                    }
                }
            }
        }
        
        return mapper.writeValueAsString(allSigmets);
    }
    
    private static List<String> rejectionLogs = new ArrayList<>();
    
    private Map<String, Object> parseSigmetCoords(String line, String fir) {
        try {
            // Extrair número do SIGMET
            String sigmetNumber = "?";
            Pattern numberPattern = Pattern.compile(fir + "\\s+SIGMET\\s+(\\d+)");
            Matcher numberMatcher = numberPattern.matcher(line);
            if (numberMatcher.find()) {
                sigmetNumber = numberMatcher.group(1);
            }
            
            // Extrair período de validade VALID DDHHMM/DDHHMM
            Pattern validPattern = Pattern.compile("VALID\\s+(\\d{6})/(\\d{6})");
            Matcher validMatcher = validPattern.matcher(line);
            log.info(String.valueOf("🔍 DEBUG PARSING: " + line.substring(0, Math.min(100, line.length()))));
            if (!validMatcher.find()) {
                log.info("❌ REGEX VALID não encontrou período!");
                return null;
            }
            
            String startTime = validMatcher.group(1);
            String endTime = validMatcher.group(2);
            log.info(String.valueOf("✅ PERÍODO EXTRAÍDO: " + startTime + "/" + endTime));
            
            // Verificar se ainda é válido (endTime > agora UTC)
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            int currentDay = now.getDayOfMonth();
            int currentHour = now.getHour();
            int currentMinute = now.getMinute();
            
            // Parse do endTime: DDHHMM
            int endDay = Integer.parseInt(endTime.substring(0, 2));
            int endHour = Integer.parseInt(endTime.substring(2, 4));
            int endMinute = Integer.parseInt(endTime.substring(4, 6));
            
            // SIGMET expirado se:
            if (endDay < currentDay || 
                (endDay == currentDay && endHour < currentHour) ||
                (endDay == currentDay && endHour == currentHour && endMinute <= currentMinute)) {
                String msg = "❌ REJEITADO - SIGMET expirado: " + line.substring(0, Math.min(100, line.length()));
                log.info(String.valueOf(msg));
                rejectionLogs.add(msg);
                return null; // SIGMET expirado
            }
            
            // Extrair coordenadas entre WI e TOP/FL/STNR/MOV
            int wiIndex = line.indexOf("WI ");
            int topIndex = line.indexOf(" TOP ");
            int flIndex = line.indexOf(" FL");
            int stnrIndex = line.indexOf(" STNR");
            int movIndex = line.indexOf(" MOV");

            int endIndex = topIndex != -1 ? topIndex
                         : flIndex != -1 ? flIndex
                         : stnrIndex != -1 ? stnrIndex
                         : movIndex != -1 ? movIndex
                         : -1;

            if (wiIndex == -1 || endIndex == -1) {
                String msg = "❌ REJEITADO - Sem coordenadas WI/TOP/FL: " + line.substring(0, Math.min(100, line.length()));
                log.info(String.valueOf(msg));
                rejectionLogs.add(msg);
                return null;
            }
            
            String coordSection = line.substring(wiIndex + 3, endIndex);
            String[] coords = coordSection.split(" - ");
            
            List<List<Double>> coordinates = new ArrayList<>();
            
            for (String coord : coords) {
                coord = coord.trim();
                
                if (coord.matches("^[NS]\\d{4} [WE]\\d{5}$")) {
                    String[] parts = coord.split(" ");
                    
                    String latStr = parts[0];
                    double lat = Double.parseDouble(latStr.substring(1, 3)) + Double.parseDouble(latStr.substring(3, 5)) / 60.0;
                    if (latStr.charAt(0) == 'S') lat = -lat;
                    
                    String lonStr = parts[1];
                    double lon = Double.parseDouble(lonStr.substring(1, 4)) + Double.parseDouble(lonStr.substring(4, 6)) / 60.0;
                    if (lonStr.charAt(0) == 'W') lon = -lon;
                    
                    coordinates.add(Arrays.asList(lon, lat));
                }
            }
            
            if (coordinates.size() < 3) {
                String msg = "❌ REJEITADO - Menos de 3 coordenadas válidas (" + coordinates.size() + "): " + line.substring(0, Math.min(100, line.length())) + " | Coords: " + coordSection;
                log.info(String.valueOf(msg));
                rejectionLogs.add(msg);
                return null;
            }
            
            // Fechar polígono
            if (!coordinates.get(0).equals(coordinates.get(coordinates.size() - 1))) {
                coordinates.add(coordinates.get(0));
            }
            
            // Determinar tipo e cor
            String sigmetType = "SIGMET";
            String color = "#FF0000";
            
            if (line.contains("EMBD TS") || line.contains("FRQ TS")) {
                sigmetType = "THUNDERSTORM";
                color = "#FF0000";
            } else if (line.contains("SEV ICE")) {
                sigmetType = "ICING";
                color = "#00BFFF";
            } else if (line.contains("SEV TURB")) {
                sigmetType = "TURBULENCE";
                color = "#FFA500";
            }
            
            Map<String, Object> sigmet = new HashMap<>();
            sigmet.put("type", "Feature");
            
            Map<String, Object> geometry = new HashMap<>();
            geometry.put("type", "Polygon");
            geometry.put("coordinates", Arrays.asList(coordinates));
            sigmet.put("geometry", geometry);
            
            Map<String, Object> properties = new HashMap<>();
            properties.put("fir", fir);
            properties.put("sigmetNumber", sigmetNumber);
            properties.put("sigmetType", sigmetType);
            properties.put("color", color);
            properties.put("validPeriod", startTime + "/" + endTime);
            log.info(String.valueOf("🔍 DEBUG MAP: validPeriod = " + (startTime + "/" + endTime)));
            properties.put("text", line);
            sigmet.put("properties", properties);
            
            return sigmet;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    // Métodos METAR
    public String getMetarForAirport(String icao) throws IOException, InterruptedException {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String dataIni = String.format("%04d%02d%02d00", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String dataFim = String.format("%04d%02d%02d23", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        
        String endpoint = String.format("?local=%s&msg=METAR&data_ini=%s&data_fim=%s&data_hora=nao", 
                                       icao, dataIni, dataFim);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + endpoint))
            .header("Authorization", "Bearer " + sharedToken)
            .header("Accept", "text/plain")
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(10))
            .build();
            
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return response.body();
        }
        return "";
    }
    
    public String getTafForAirport(String icao) throws IOException, InterruptedException {
        if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) {
            authenticate();
        }
        
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // TAF pode ter sido emitido no dia anterior — buscar desde 6h atrás
        LocalDateTime ini = now.minusHours(6);

        String dataIni = String.format("%04d%02d%02d%02d", ini.getYear(), ini.getMonthValue(), ini.getDayOfMonth(), ini.getHour());
        String dataFim = String.format("%04d%02d%02d23", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        
        String endpoint = String.format("?local=%s&msg=TAF&data_ini=%s&data_fim=%s&data_hora=nao", 
                                       icao, dataIni, dataFim);
        
        log.info("🔍 Buscando TAF para {} de {} até {}", icao, dataIni, dataFim);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + endpoint))
            .header("Authorization", "Bearer " + sharedToken)
            .header("Accept", "text/plain")
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(10))
            .build();
            
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        log.info(String.valueOf("📡 TAF Response status: " + response.statusCode()));

        // Se 403, token expirou — invalidar Redis e reautenticar
        if (response.statusCode() == 403) {
            synchronized (tokenLock) { sharedToken = null; tokenExpiry = 0; }
            if (jedisPool != null) { try (Jedis j = jedisPool.getResource()) { j.del(REDIS_TOKEN_KEY); } catch (Exception ignored) {} }
            authenticate();
            return getTafForAirport(icao);
        }
        
        if (response.statusCode() == 200) {
            String tafResponse = response.body().trim();
            log.info("📋 TAF raw response para {}: [{}]", icao, tafResponse);

            if (tafResponse.isEmpty()) return "";

            // Múltiplos TAFs separados por "=" — pegar o último
            if (tafResponse.contains("=")) {
                String[] parts = tafResponse.split("=");
                for (int i = parts.length - 1; i >= 0; i--) {
                    String candidate = parts[i].trim();
                    if (candidate.contains("TAF")) {
                        // Garantir que começa com "TAF"
                        int idx = candidate.indexOf("TAF");
                        return candidate.substring(idx).trim();
                    }
                }
            }

            // TAF único sem separador
            if (tafResponse.contains("TAF")) {
                int idx = tafResponse.indexOf("TAF");
                return tafResponse.substring(idx).trim();
            }

            return tafResponse;
        }
        return "";
    }
    
    public String getLatestMetar(String icao) throws IOException, InterruptedException {
        if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) {
            authenticate();
        }
        
        String metarData = getMetarForAirport(icao);
        
        String[] parts = metarData.split("=");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].contains("METAR") && parts[i].contains(icao)) {
                return parts[i].trim();
            }
        }
        
        return "METAR não disponível";
    }
    
    public String getMetarCondition(String icao) throws IOException, InterruptedException {
        if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) {
            authenticate();
        }
        
        String latestMetar = getLatestMetar(icao);
        if (latestMetar.equals("METAR não disponível")) {
            return "UNKNOWN";
        }
        
        try {
            int visibility = 9999;
            String[] parts = latestMetar.split(" ");
            for (String part : parts) {
                if (part.matches("\\d{4}") && !part.matches("\\d{4}Z")) {
                    visibility = Integer.parseInt(part);
                    break;
                }
            }
            
            int ceiling = 9999;
            for (String part : parts) {
                if (part.startsWith("BKN") || part.startsWith("OVC")) {
                    String heightStr = part.substring(3);
                    if (heightStr.matches("\\d{3}")) {
                        ceiling = Integer.parseInt(heightStr) * 100;
                        break;
                    }
                }
            }
            
            if (visibility < 1000 || ceiling < 200) {
                return "LIFR";
            } else if (visibility < 3000 || ceiling < 500) {
                return "IFR";
            } else if (visibility < 5000 || ceiling < 1500) {
                return "MVFR";
            } else {
                return "VFR";
            }
            
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    public String getMetar(String icao) throws IOException, InterruptedException {
        return getMetarForAirport(icao);
    }
    
    public static List<String> getRejectionLogs() {
        return new ArrayList<>(rejectionLogs);
    }
    
    public static void clearRejectionLogs() {
        rejectionLogs.clear();
    }

    public String getAirmetsJson() throws IOException, InterruptedException {
        synchronized (tokenLock) {
            if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) {
                authenticate();
            }
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String dataIni = String.format("%04d%02d%02d%02d", now.minusHours(6).getYear(), now.minusHours(6).getMonthValue(), now.minusHours(6).getDayOfMonth(), now.minusHours(6).getHour());
        String dataFim = String.format("%04d%02d%02d%02d", now.plusHours(6).getYear(), now.plusHours(6).getMonthValue(), now.plusHours(6).getDayOfMonth(), now.plusHours(6).getHour());

        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> cancelled = new HashSet<>();

        for (String fir : FIRS) {
            String body = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    Thread.sleep(attempt == 1 ? 300 : 1500);
                    String endpoint = String.format("?local=%s&msg=AIRMET&data_ini=%s&data_fim=%s&data_hora=nao", fir, dataIni, dataFim);
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + endpoint))
                        .header("Authorization", "Bearer " + sharedToken)
                        .header("Accept", "text/plain")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(30))
                        .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 403) {
                        synchronized (tokenLock) { sharedToken = null; tokenExpiry = 0; }
                        if (jedisPool != null) { try (Jedis j = jedisPool.getResource()) { j.del(REDIS_TOKEN_KEY); } catch (Exception ignored) {} }
                        authenticate();
                        continue;
                    }
                    if (response.statusCode() == 200) { body = response.body(); break; }
                    log.warn("AIRMET FIR {} attempt {} status={}", fir, attempt, response.statusCode());
                } catch (Exception e) {
                    log.warn("AIRMET FIR {} attempt {} erro: {}", fir, attempt, e.getMessage());
                }
            }
            if (body == null || body.isBlank()) continue;
            for (String msg : body.split("=")) {
                String m = msg.trim();
                if (!m.contains("AIRMET")) continue;
                Matcher cnl = Pattern.compile("CNL\\s+AIRMET\\s+(\\d+)").matcher(m);
                if (cnl.find()) { cancelled.add(fir + "_" + cnl.group(1)); continue; }
                if (!m.contains("WI ")) continue;
                Map<String, Object> airmet = parseAirmetCoords(m, fir);
                if (airmet != null) result.add(airmet);
            }
        }

        result.removeIf(a -> {
            Map<?,?> props = (Map<?,?>) a.get("properties");
            return cancelled.contains(props.get("fir") + "_" + props.get("airmetNumber"));
        });

        log.info("AIRMETs válidos retornados: {}", result.size());
        return mapper.writeValueAsString(result);
    }

    private Map<String, Object> parseAirmetCoords(String line, String fir) {
        try {
            Pattern validPat = Pattern.compile("VALID\\s+(\\d{6})/(\\d{6})");
            Matcher vm = validPat.matcher(line);
            if (!vm.find()) return null;
            String startTime = vm.group(1), endTime = vm.group(2);

            // Verificar validade — considera rollover de mês
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            int endDay  = Integer.parseInt(endTime.substring(0, 2));
            int endHour = Integer.parseInt(endTime.substring(2, 4));
            int endMin  = Integer.parseInt(endTime.substring(4, 6));
            int month = now.getMonthValue(), year = now.getYear();
            if (endDay < now.getDayOfMonth() - 1) { month++; if (month > 12) { month = 1; year++; } }
            LocalDateTime endDt;
            try { endDt = LocalDateTime.of(year, month, endDay, endHour, endMin); }
            catch (Exception e) { endDt = now.plusHours(1); }
            if (endDt.isBefore(now)) return null;

            // Extrair número
            String airmetNumber = "?";
            Matcher nm = Pattern.compile(fir + "\\s+AIRMET\\s+(\\d+)").matcher(line);
            if (nm.find()) airmetNumber = nm.group(1);

            // Extrair coordenadas entre WI e fim da seção
            int wiIdx = line.indexOf("WI ");
            if (wiIdx == -1) return null;
            String coordRaw = line.substring(wiIdx + 3).replaceAll("\\s+(STNR|MOV|NC).*", "").trim();

            List<List<Double>> coords = new ArrayList<>();
            for (String c : coordRaw.split(" - ")) {
                c = c.trim();
                if (!c.matches("^[NS]\\d{4} [WE]\\d{5}$")) continue;
                String[] p = c.split(" ");
                double lat = Double.parseDouble(p[0].substring(1,3)) + Double.parseDouble(p[0].substring(3,5))/60.0;
                if (p[0].charAt(0) == 'S') lat = -lat;
                double lon = Double.parseDouble(p[1].substring(1,4)) + Double.parseDouble(p[1].substring(4,6))/60.0;
                if (p[1].charAt(0) == 'W') lon = -lon;
                coords.add(Arrays.asList(lon, lat));
            }
            if (coords.size() < 3) return null;
            if (!coords.get(0).equals(coords.get(coords.size()-1))) coords.add(coords.get(0));

            // Cor por fenômeno
            String color = "#1A3A6B"; // azul escuro padrão
            if (line.contains("TS"))                          color = "#FFA500";
            else if (line.contains("ICE") || line.contains("FZRA")) color = "#00CED1";
            else if (line.contains("TURB"))                   color = "#FF8C00";
            else if (line.contains("MTW"))                    color = "#DA70D6";

            Map<String, Object> geometry = new HashMap<>();
            geometry.put("type", "Polygon");
            geometry.put("coordinates", Arrays.asList(coords));

            Map<String, Object> props = new HashMap<>();
            props.put("fir", fir);
            props.put("airmetNumber", airmetNumber);
            props.put("color", color);
            props.put("validPeriod", startTime + "/" + endTime);
            props.put("text", line.trim());

            Map<String, Object> feature = new HashMap<>();
            feature.put("type", "Feature");
            feature.put("geometry", geometry);
            feature.put("properties", props);
            return feature;

        } catch (Exception e) {
            return null;
        }
    }
}
