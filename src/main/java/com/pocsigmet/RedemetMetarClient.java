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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.ZoneOffset;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@org.springframework.stereotype.Component
public class RedemetMetarClient {
    private static final Logger log = LoggerFactory.getLogger(RedemetMetarClient.class);
    private static final String BASE_URL = "https://opmet.decea.mil.br/redemet/consulta_redemet";
    @org.springframework.beans.factory.annotation.Value("${app.redemet.username:testeicaolima}")
    private String username;
    @org.springframework.beans.factory.annotation.Value("${app.redemet.password:Mudar12345@}")
    private String password;
    
    private final HttpClient client;
    private final ObjectMapper mapper;
    
    // Token compartilhado entre todas as instâncias
    private static String sharedToken;
    private static long tokenExpiry = 0;
    private static final Object tokenLock = new Object();
    
    
    public RedemetMetarClient() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.mapper = new ObjectMapper();
    }
    
    public String authenticate() throws IOException, InterruptedException {
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
            return sharedToken;
        }
        
        throw new RuntimeException("Falha na autenticação: " + response.statusCode());
    }
    
    public String getMetarAndSpeciForAirport(String icao) throws IOException, InterruptedException {
        if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) authenticate();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime sixHoursAgo = now.minusHours(6);

        String dataIni = String.format("%04d%02d%02d%02d", sixHoursAgo.getYear(), sixHoursAgo.getMonthValue(), sixHoursAgo.getDayOfMonth(), sixHoursAgo.getHour());
        String dataFim = String.format("%04d%02d%02d%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour() + 1);

        String endpoint = String.format("?local=%s&msg=METAR&data_ini=%s&data_fim=%s&data_hora=nao",
                icao, dataIni, dataFim);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + sharedToken)
                .header("Accept", "text/plain")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String data = response.statusCode() == 200 ? response.body() : "";
        log.info("🔍 METAR+SPECI para {}: {} mensagens", icao, data.isEmpty() ? 0 : data.split("=").length);
        return data;
    }

    /**
     * Busca METAR+SPECI mais recente para uma lista de ICAOs em uma única requisição.
     * Retorna Map<icao, mensagem mais recente>.
     */
    public java.util.Map<String, String> getLatestMetarBulk(java.util.List<String> icaos)
            throws IOException, InterruptedException {
        if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) authenticate();

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime sixHoursAgo = now.minusHours(6);
        String dataIni = String.format("%04d%02d%02d%02d", sixHoursAgo.getYear(), sixHoursAgo.getMonthValue(), sixHoursAgo.getDayOfMonth(), sixHoursAgo.getHour());
        String dataFim = String.format("%04d%02d%02d%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth(), now.getHour() + 1);

        String localParam = String.join(",", icaos);
        String endpoint = String.format("?local=%s&msg=METAR&data_ini=%s&data_fim=%s&data_hora=nao",
                localParam, dataIni, dataFim);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + sharedToken)
                .header("Accept", "text/plain")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.statusCode() == 200 ? response.body() : "";
        log.info("🔍 Bulk METAR para {} ICAOs: {} mensagens", icaos.size(), body.isEmpty() ? 0 : body.split("=").length);

        // Para cada ICAO, encontra a mensagem mais recente na resposta
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        for (String icao : icaos) {
            result.put(icao, extractLatest(body, icao));
        }
        return result;
    }

    /** Extrai a mensagem mais recente (maior timestamp DDHHMMZ) para um ICAO numa resposta bulk. */
    static String extractLatest(String body, String icao) {
        if (body == null || body.isEmpty()) return "METAR/SPECI não disponível";
        String[] parts = body.split("=");
        String latest = "";
        int latestTime = 0;
        for (String part : parts) {
            part = part.trim();
            if ((part.contains("METAR") || part.contains("SPECI")) && part.contains(icao)) {
                for (String token : part.split(" ")) {
                    if (token.matches("\\d{6}Z")) {
                        try {
                            int t = Integer.parseInt(token.substring(0, 6));
                            boolean isCor = part.contains("METAR COR") || part.contains("SPECI COR");
                            boolean currentIsCor = latest.contains("METAR COR") || latest.contains("SPECI COR");
                            // prefere maior timestamp; empate: COR vence
                            if (t > latestTime || (t == latestTime && isCor && !currentIsCor)) {
                                latestTime = t; latest = part;
                            }
                        } catch (NumberFormatException ignored) {}
                        break;
                    }
                }
            }
        }
        return latest.isEmpty() ? "METAR/SPECI não disponível" : latest;
    }
    
    public String getLatestMetarOrSpeci(String icao) throws IOException, InterruptedException {
        if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) {
            authenticate();
        }
        
        String allData = getMetarAndSpeciForAirport(icao);
        if (allData.isEmpty()) {
            return "METAR/SPECI não disponível";
        }
        
        // Separar por = e encontrar a mensagem mais recente por timestamp
        String[] parts = allData.split("=");
        String latestMessage = "";
        int latestTimeInt = 0;
        
        log.info(String.valueOf("🔍 Analisando " + parts.length + " mensagens para " + icao));
        
        for (String part : parts) {
            part = part.trim();
            if ((part.contains("METAR") || part.contains("SPECI")) && part.contains(icao)) {
                // Extrair timestamp DDHHMMZ
                String[] tokens = part.split(" ");
                for (String token : tokens) {
                    if (token.matches("\\d{6}Z")) {
                        try {
                            int timeInt = Integer.parseInt(token.substring(0, 6));
                            log.info("🔍 " + (part.contains("METAR") ? "METAR" : "SPECI") + " " + token + " = " + timeInt);
                            if (timeInt > latestTimeInt) {
                                latestTimeInt = timeInt;
                                latestMessage = part;
                                log.info(String.valueOf("✅ Nova mensagem mais recente: " + token));
                            }
                        } catch (NumberFormatException e) {
                            // Ignorar timestamps inválidos
                        }
                        break;
                    }
                }
            }
        }
        
        return latestMessage.isEmpty() ? "METAR/SPECI não disponível" : latestMessage;
    }
    
    public String getMetarCondition(String icao) throws IOException, InterruptedException {
        if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) {
            authenticate();
        }
        
        String latestMessage = getLatestMetarOrSpeci(icao);
        if (latestMessage.equals("METAR/SPECI não disponível")) {
            return "UNKNOWN";
        }
        if (latestMessage.contains("CAVOK")) return "VFR";
        
        try {
            int visibility = 9999;
            String[] parts = latestMessage.split(" ");
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
    
    // Cache para avisos — Redis compartilhado entre containers
    private static final String REDIS_AVISOS_KEY     = "cache:avisos_aerodromo";
    private static final String REDIS_AVISOS_RAW_KEY = "cache:avisos_aerodromo_raw";
    private static final int    AVISOS_TTL_SECONDS   = 300; // 5 minutos

    public String getAvisoAerodromoRaw() throws IOException, InterruptedException {
        try (redis.clients.jedis.Jedis j = RedisMetarCacheService.getJedis()) {
            if (j != null) { String v = j.get(REDIS_AVISOS_RAW_KEY); if (v != null) return v; }
        } catch (Exception ignored) {}
        getAvisoAerodromo();
        try (redis.clients.jedis.Jedis j = RedisMetarCacheService.getJedis()) {
            if (j != null) { String v = j.get(REDIS_AVISOS_RAW_KEY); if (v != null) return v; }
        } catch (Exception ignored) {}
        return "";
    }
    
    public String getAvisoAerodromo() throws IOException, InterruptedException {
        // Verificar cache Redis compartilhado
        try (redis.clients.jedis.Jedis j = RedisMetarCacheService.getJedis()) {
            if (j != null) { String v = j.get(REDIS_AVISOS_KEY); if (v != null) return v; }
        } catch (Exception ignored) {}
        
        if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) {
            authenticate();
        }
        
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime threeHoursAgo = now.minusHours(3);  // Alinhar com SIGMETs
        LocalDateTime sixHoursAhead = now.plusHours(6); // 6h para frente
        
        String dataIni = String.format("%04d%02d%02d%02d", threeHoursAgo.getYear(), threeHoursAgo.getMonthValue(), threeHoursAgo.getDayOfMonth(), threeHoursAgo.getHour());
        String dataFim = String.format("%04d%02d%02d%02d", sixHoursAhead.getYear(), sixHoursAhead.getMonthValue(), sixHoursAhead.getDayOfMonth(), sixHoursAhead.getHour());
        
        log.info(String.valueOf("🔍 Buscando AVISO_AERODROMO (-3h até +6h): " + dataIni + " até " + dataFim));
        
        // Buscar avisos para todos os aeródromos da lista estática
        String[] airportsWithAvisos = AirportConfig.getAirports().stream()
            .map(a -> (String) a[0]).toArray(String[]::new);
        StringBuilder allAvisos = new StringBuilder();
        
        for (String airport : airportsWithAvisos) {
            String endpoint = String.format("?local=%s&msg=AVISO_AERODROMO&data_ini=%s&data_fim=%s&data_hora=nao",
                                           airport, dataIni, dataFim);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + sharedToken)
                .header("Accept", "text/plain")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30))
                .build();
                
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && !response.body().isEmpty()) {
                if (allAvisos.length() > 0) allAvisos.append("=");
                allAvisos.append(response.body());
            }
        }
        
        String avisoData = allAvisos.toString();

        // salva raw no Redis antes de expandir
        try (redis.clients.jedis.Jedis j = RedisMetarCacheService.getJedis()) {
            if (j != null) j.setex(REDIS_AVISOS_RAW_KEY, AVISOS_TTL_SECONDS, avisoData);
        } catch (Exception ignored) {}

        // Expandir ICAOs do cabeçalho: "SBAN/SBCN/SBGO AD WRNG..." -> duplicar aviso pra cada ICAO
        // Garante que ICAOs que a API não retorna diretamente também sejam encontrados
        StringBuilder expanded = new StringBuilder(avisoData);
        for (String aviso : avisoData.split("=")) {
            Matcher m = Pattern.compile("([A-Z]{2}[A-Z0-9]{2}(?:/[A-Z]{2}[A-Z0-9]{2})+)\\s+(AD WRNG|WS WRNG)").matcher(aviso.trim());
            if (m.find()) {
                for (String icao : m.group(1).split("/")) {
                    if (!aviso.contains(icao + " AD WRNG") && !aviso.contains(icao + " WS WRNG")) {
                        expanded.append("=").append(aviso.trim().replaceFirst("[A-Z]{2}[A-Z0-9]{2}(?:/[A-Z]{2}[A-Z0-9]{2})+", icao));
                    }
                }
            }
        }
        avisoData = expanded.toString();

        log.info("🔍 AVISO_AERODROMO: " + (avisoData.isEmpty() ? "0" : avisoData.split("=").length) + " mensagens");

        // Atualizar cache Redis
        try (redis.clients.jedis.Jedis j = RedisMetarCacheService.getJedis()) {
            if (j != null) j.setex(REDIS_AVISOS_KEY, AVISOS_TTL_SECONDS, avisoData);
        } catch (Exception ignored) {}

        return avisoData;
    }
    
    public String getAvisoCortanteVento() throws IOException, InterruptedException {
        if (sharedToken == null || System.currentTimeMillis() >= tokenExpiry) {
            authenticate();
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime threeHoursAgo = now.minusHours(3);
        LocalDateTime oneHourAhead = now.plusHours(1);

        String dataIni = String.format("%04d%02d%02d%02d", threeHoursAgo.getYear(), threeHoursAgo.getMonthValue(), threeHoursAgo.getDayOfMonth(), threeHoursAgo.getHour());
        String dataFim = String.format("%04d%02d%02d%02d", oneHourAhead.getYear(), oneHourAhead.getMonthValue(), oneHourAhead.getDayOfMonth(), oneHourAhead.getHour());

        log.info(String.valueOf("🔍 Buscando AVISO_CORTANTE_VENTO: " + dataIni + " até " + dataFim));

        StringBuilder allAvisos = new StringBuilder();
        for (String airport : AirportConfig.getAirports().stream().map(a -> (String) a[0]).toArray(String[]::new)) {
            String endpoint = String.format("?local=%s&msg=AVISO_CORTANTE_VENTO&data_ini=%s&data_fim=%s&data_hora=nao", airport, dataIni, dataFim);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Authorization", "Bearer " + sharedToken)
                .header("Accept", "text/plain")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(30))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && !response.body().isEmpty()) {
                if (allAvisos.length() > 0) allAvisos.append("=");
                allAvisos.append(response.body());
            }
        }

        String avisoData = allAvisos.toString();
        log.info("🔍 AVISO_CORTANTE_VENTO: " + (avisoData.isEmpty() ? "0" : avisoData.split("=").length) + " mensagens");
        return avisoData;
    }
    
    public boolean hasAvisoForAirport(String icao) throws IOException, InterruptedException {
        String avisoAerodromo = getAvisoAerodromo();
        String avisoCortante = getAvisoCortanteVento();
        
        String allAvisos = avisoAerodromo;
        if (!avisoCortante.isEmpty()) {
            allAvisos += (allAvisos.isEmpty() ? "" : "=") + avisoCortante;
        }
        
        if (allAvisos.isEmpty()) {
            return false;
        }
        
        // Verificar se o ICAO está mencionado nos avisos válidos
        String[] avisos = allAvisos.split("=");
        for (String aviso : avisos) {
            if ((aviso.contains("AD WRNG") || aviso.contains("WS WRNG")) && aviso.contains(icao)) {
                // Verificar se ainda é válido
                if (aviso.matches(".*VALID \\d{6}/\\d{6}.*")) {
                    // Extrair período de validade
                    String validPattern = aviso.replaceAll(".*VALID (\\d{6})/(\\d{6}).*", "$1/$2");
                    String[] validTimes = validPattern.split("/");
                    
                    if (validTimes.length == 2) {
                        try {
                            // Hora atual em formato YYYYMMDDHHMM
                            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                            
                            // Avisos usam formato DDHHMM, converter para YYYYMMDDHHMM
                            String day = validTimes[0].substring(0, 2);
                            String hourMin1 = validTimes[0].substring(2, 6);
                            String day2 = validTimes[1].substring(0, 2);
                            String hourMin2 = validTimes[1].substring(2, 6);
                            
                            String validFrom = String.format("%04d%02d%s%s", 
                                now.getYear(), now.getMonthValue(), day, hourMin1);
                            String validTo = String.format("%04d%02d%s%s", 
                                now.getYear(), now.getMonthValue(), day2, hourMin2);
                            
                            String currentTime = String.format("%04d%02d%02d%02d%02d", 
                                now.getYear(), now.getMonthValue(), now.getDayOfMonth(), 
                                now.getHour(), now.getMinute());
                            
                            // Verificar se está dentro do período
                            if (currentTime.compareTo(validFrom) >= 0 && currentTime.compareTo(validTo) <= 0) {
                                log.info(String.valueOf("🔍 Aviso VÁLIDO para " + icao + ": " + aviso.substring(0, Math.min(100, aviso.length()))));
                                return true;
                            } else {
                                log.info("⏰ Aviso EXPIRADO para " + icao + " (válido até " + validTo + ", agora " + currentTime + ")");
                            }
                        } catch (Exception e) {
                            log.info(String.valueOf("❌ Erro ao validar tempo do aviso: " + e.getMessage()));
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    public String getAvisoTextForAirport(String icao) throws IOException, InterruptedException {
        String avisoAerodromo = getAvisoAerodromo();
        String avisoCortante = getAvisoCortanteVento();
        
        String allAvisos = avisoAerodromo;
        if (!avisoCortante.isEmpty()) {
            allAvisos += (allAvisos.isEmpty() ? "" : "=") + avisoCortante;
        }
        
        if (allAvisos.isEmpty()) return "";
        
        String[] avisos = allAvisos.split("=");
        String latestAviso = "";
        int latestWrngNumber = -1;
        
        for (String aviso : avisos) {
            if ((aviso.contains("AD WRNG") || aviso.contains("WS WRNG")) && aviso.contains(icao)) {
                try {
                    String wrngPattern = aviso.replaceAll(".*(AD WRNG|WS WRNG) (\\d+).*", "$2");
                    int wrngNumber = Integer.parseInt(wrngPattern);
                    
                    if (wrngNumber > latestWrngNumber) {
                        latestWrngNumber = wrngNumber;
                        latestAviso = aviso.trim();
                    }
                } catch (Exception e) {
                    if (latestAviso.isEmpty()) {
                        latestAviso = aviso.trim();
                    }
                }
            }
        }
        return latestAviso;
    }
    
    // Métodos de compatibilidade
    public String getMetarForAirport(String icao) throws IOException, InterruptedException {
        return getMetarAndSpeciForAirport(icao);
    }
    
    public String getLatestMetar(String icao) throws IOException, InterruptedException {
        return getLatestMetarOrSpeci(icao);
    }
    
    public String getMetar(String icao) throws IOException, InterruptedException {
        return getMetarAndSpeciForAirport(icao);
    }
}
