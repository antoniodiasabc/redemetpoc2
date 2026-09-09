package com.pocsigmet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

/**
 * Handler para integração OPMET via SSE (Server-Sent Events)
 * Replica funcionalidade do OpmetSwingClient para ambiente web
 */
public class OpmetWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(OpmetWebSocketHandler.class);
    
    private static final String URL_BASE = "https://opmet.decea.mil.br";
    private static final String LOGIN_ENDPOINT = "/adm/login";
    private static final String SUBSCRIBE_ENDPOINT = "/iwxxm/subscribe";
    private static String USERNAME = System.getenv().getOrDefault("OPMET_USERNAME", "iwxxm2");
    private static String PASSWORD = System.getenv().getOrDefault("OPMET_PASSWORD", "Mudar123@");

    // Redis keys
    private static final String REDIS_CONNECTED   = "opmet:connected";
    private static final String REDIS_TOKEN       = "opmet:token";
    private static final String REDIS_MESSAGES    = "opmet:messages";
    private static final String REDIS_BROADCAST   = "opmet:broadcast";
    private static final int    REDIS_TTL         = 7200; // 2h
    private static final String INSTANCE_ID       = UUID.randomUUID().toString().substring(0, 8);

    private static final Set<OpmetSession> sessions = ConcurrentHashMap.newKeySet();
    private static final int MAX_RECENT_MESSAGES = 50;
    private static String currentToken = null;
    private static volatile boolean isConnected = false;
    private static volatile long lastMessageTime = 0;
    private static CompletableFuture<Void> sseTask;
    private static HttpURLConnection activeConnection;
    private static ScheduledExecutorService connectionMonitor;
    private static boolean useIwxxmParameter = false;

    // Limpa estado Redis inconsistente ao iniciar (ex: após rebuild)
    static {
        redisDel(REDIS_CONNECTED);
        redisDel("opmet:leader");
        log.info("🧹 Estado OPMET Redis limpo na inicialização");
    }

    // Inicia conexão SSE permanente independente de sessões
    public static void initPermanentConnection() {
        log.info("🚀 Iniciando conexão OPMET permanente...");
        startPubSubListener();
        startOpmetConnection();
    }

    /** Assina o channel Redis — recebe broadcasts de outras instâncias */
    private static void startPubSubListener() {
        Thread t = new Thread(() -> {
            while (true) {
                try (Jedis j = RedisMetarCacheService.getJedis()) {
                    if (j == null) { Thread.sleep(5000); continue; }
                    j.subscribe(new JedisPubSub() {
                        public void onMessage(String channel, String message) {
                            // ignora mensagens publicadas por esta própria instância
                            if (message.contains("\"origin\":\"" + INSTANCE_ID + "\"")) return;
                            // remove o campo origin antes de repassar ao frontend
                            String clean = message.replaceAll(",\"origin\":\"[^\"]+\"", "");
                            broadcastLocalOnly(clean);
                        }
                    }, REDIS_BROADCAST);
                } catch (Exception e) {
                    log.warn("PubSub Redis reconectando: {}", e.getMessage());
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { return; }
                }
            }
        }, "opmet-pubsub-listener");
        t.setDaemon(true);
        t.start();
    }

    private static void redisSet(String key, String value) {
        try (Jedis j = RedisMetarCacheService.getJedis()) {
            if (j != null) j.setex(key, REDIS_TTL, value);
        } catch (Exception e) { /* Redis opcional */ }
    }

    private static String redisGet(String key) {
        try (Jedis j = RedisMetarCacheService.getJedis()) {
            return j != null ? j.get(key) : null;
        } catch (Exception e) { return null; }
    }

    private static void redisDel(String key) {
        try (Jedis j = RedisMetarCacheService.getJedis()) {
            if (j != null) j.del(key);
        } catch (Exception e) { /* Redis opcional */ }
    }

    private static void redisAppendMessage(String msg) {
        try (Jedis j = RedisMetarCacheService.getJedis()) {
            if (j == null) return;
            j.lpush(REDIS_MESSAGES, msg);
            j.ltrim(REDIS_MESSAGES, 0, MAX_RECENT_MESSAGES - 1);
            j.expire(REDIS_MESSAGES, REDIS_TTL);
        } catch (Exception e) { /* Redis opcional */ }
    }

    public static String[] getRecentMessages() {
        // Tentar Redis primeiro (compartilhado entre containers)
        try (Jedis j = RedisMetarCacheService.getJedis()) {
            if (j != null) {
                List<String> msgs = j.lrange(REDIS_MESSAGES, 0, MAX_RECENT_MESSAGES - 1);
                if (msgs != null && !msgs.isEmpty()) {
                    Collections.reverse(msgs); // mais antigo primeiro
                    return msgs.toArray(new String[0]);
                }
            }
        } catch (Exception e) { /* fallback abaixo */ }
        return new String[0];
    }

    // --- Estado compartilhado ---

    public static boolean isConnected() {
        // Redis é a fonte de verdade — qualquer container pode ter feito login
        String val = redisGet(REDIS_CONNECTED);
        if ("true".equals(val)) return true;
        return isConnected; // fallback local
    }

    // --- Sessões ---

    /** Registra sessão apenas para receber broadcasts, sem disparar conexão OPMET. */
    public static void registerBroadcastSession(OpmetSession session) {
        sessions.add(session);
    }

    public static void addSession(OpmetSession session) {
        sessions.add(session);
        log.info("🔌 Nova sessão OPMET: " + sessions.size() + " conectadas");

        if (testingMode) {
            sendToSession(session, createStatusMessage("connected", true));
            return;
        }

        // Só inicia conexão se nenhum container já está conectado (Redis como lock)
        boolean alreadyConnected = "true".equals(redisGet(REDIS_CONNECTED));
        if (!alreadyConnected && !isConnected) {
            // Tenta adquirir lock de leader — apenas um container conecta ao SSE
            try (Jedis j = RedisMetarCacheService.getJedis()) {
                if (j != null) {
                    Long acquired = j.setnx("opmet:leader", "1");
                    if (acquired == 1) {
                        j.expire("opmet:leader", REDIS_TTL);
                    } else {
                        // Outro container já é leader — não conecta SSE
                        sendToSession(session, createStatusMessage("connected", isConnected()));
                        return;
                    }
                }
            } catch (Exception e) { /* Redis opcional */ }
            // Reaproveitar token do Redis se disponível
            String savedToken = redisGet(REDIS_TOKEN);
            if (savedToken != null) {
                currentToken = savedToken;
                log.info("♻️ Token OPMET reutilizado do Redis");
                isConnected = true;
                redisSet(REDIS_CONNECTED, "true");
                startConnectionMonitor();
                startSSE();
            } else {
                startOpmetConnection();
            }
        }

        sendToSession(session, createStatusMessage("connected", isConnected()));
    }

    public static void removeSession(OpmetSession session) {
        sessions.remove(session);
        log.info("🔌 Sessão OPMET removida: " + sessions.size() + " conectadas");
        if (sessions.isEmpty()) {
            stopOpmetConnection();
        }
    }
    
    private static volatile CompletableFuture<Void> loginFuture;

    private static void startOpmetConnection() {
        log.info("🚀 Iniciando conexão OPMET...");
        
        loginFuture = CompletableFuture.runAsync(() -> {
            int loginAttempts = 0;
            while (loginAttempts < 3 && !login()) {
                loginAttempts++;
                try { Thread.sleep(3000); } catch (InterruptedException ie) { return; }
            }
            
            if (loginAttempts < 3) {
                isConnected = true;
                redisSet(REDIS_CONNECTED, "true");
                broadcastMessage(createStatusMessage("login_success", true));
                startConnectionMonitor();
                startSSE();
            } else {
                broadcastMessage(createStatusMessage("login_failed", false));
            }
        });
    }
    
    private static void stopOpmetConnection() {
        log.info("⏹️ Parando conexão OPMET...");
        isConnected = false;
        lastMessageTime = 0;
        redisDel(REDIS_CONNECTED);
        
        if (loginFuture != null) {
            loginFuture.cancel(true);
            loginFuture = null;
        }
        
        if (activeConnection != null) {
            try { activeConnection.disconnect(); } catch (Exception e) {}
            activeConnection = null;
        }
        
        if (sseTask != null) {
            sseTask.cancel(true);
        }
        
        if (connectionMonitor != null) {
            connectionMonitor.shutdownNow();
            connectionMonitor = null;
        }
    }
    
    private static boolean login() {
        try {
            log.info("🔐 Fazendo login OPMET...");
            
            String url = URL_BASE + LOGIN_ENDPOINT;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            
            String json = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", USERNAME, PASSWORD);
            
            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
                writer.write(json);
                writer.flush();
            }
            
            int status = conn.getResponseCode();
            log.info(String.valueOf("📡 Login status: " + status));
            
            if (status == 200) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                String responseStr = response.toString();
                
                if (responseStr.contains("authorization")) {
                    int start = responseStr.indexOf("\"Bearer ") + 8;
                    int end = responseStr.indexOf("\"", start);
                    currentToken = responseStr.substring(start, end);
                    redisSet(REDIS_TOKEN, currentToken); // compartilhar token entre containers
                    log.info("✅ Token OPMET obtido: " + currentToken.substring(0, 20) + "...");
                    return true;
                }
            }
            
            conn.disconnect();
        } catch (Exception e) {
            log.warn(String.valueOf("❌ Erro no login OPMET: " + e.getMessage()));
        }
        return false;
    }
    
    private static void startSSE() {
        if (currentToken == null) return;
        
        sseTask = CompletableFuture.runAsync(() -> {
            StringBuilder currentMessage = new StringBuilder(); // Buffer para mensagem completa
            
            while (isConnected) {
                try {
                    String url = URL_BASE + SUBSCRIBE_ENDPOINT;
                    
                    // Adicionar parâmetro IWXXM se configurado
                    if (useIwxxmParameter) {
                        url += "?iwxxm=true";
                    }
                    
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    activeConnection = conn;
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("authorization", "Bearer " + currentToken);
                    conn.setRequestProperty("Cache-Control", "no-cache");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(90000); // 90s — detecta conexão travada
                    
                    int status = conn.getResponseCode();
                    log.info(String.valueOf("📡 SSE status: " + status));
                    
                    if (status == 200) {
                        lastMessageTime = System.currentTimeMillis();
                        broadcastMessage(createStatusMessage("sse_connected", true));
                        
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                            String line;
                            
                            while (isConnected && (line = reader.readLine()) != null) {
                                lastMessageTime = System.currentTimeMillis();
                                
                                if (line.startsWith("data:")) {
                                    String data = line.substring(5).trim();
                                    if (!data.isEmpty()) {
                                        // Acumular linhas até encontrar uma que termine com =
                                        if (currentMessage.length() > 0) {
                                            currentMessage.append(" ");
                                        }
                                        currentMessage.append(data);
                                        
                                        // Se termina com =, é fim da mensagem
                                        if (data.endsWith("=")) {
                                            broadcastMessage(createDataMessage(currentMessage.toString()));
                                            currentMessage.setLength(0); // Limpar buffer
                                        }
                                    }
                                } else if (!line.trim().isEmpty()) {
                                    // Linha sem prefixo data: - tratar igual
                                    if (currentMessage.length() > 0) {
                                        currentMessage.append(" ");
                                    }
                                    currentMessage.append(line.trim());
                                    
                                    if (line.trim().endsWith("=")) {
                                        broadcastMessage(createDataMessage(currentMessage.toString()));
                                        currentMessage.setLength(0);
                                    }
                                }
                            }
                        }
                    } else if (status == 400 || status == 406) {
                        log.warn(String.valueOf("❌ Erro fatal OPMET: " + status));
                        broadcastMessage(createStatusMessage("fatal_error", false));
                        isConnected = false;
                        break;
                    } else if (status == 403) {
                        log.info("🔄 Token expirado, renovando...");
                        if (login()) {
                            Thread.sleep(1000);
                            continue;
                        }
                    }
                    
                    conn.disconnect();
                    
                } catch (Exception e) {
                    if (isConnected) {
                        String errorMsg = e.getMessage();
                        log.warn(String.valueOf("❌ Erro SSE: " + errorMsg));
                        
                        if (errorMsg != null && errorMsg.contains("Connection reset")) {
                            log.info("🔄 Connection reset - fazendo novo login...");
                            if (login()) {
                                try { Thread.sleep(2000); } catch (InterruptedException ie) {}
                                continue;
                            }
                        }
                        
                        broadcastMessage(createStatusMessage("connection_error", false));
                        try { Thread.sleep(5000); } catch (InterruptedException ie) {}
                    }
                }
            }
        });
    }
    
    private static void startConnectionMonitor() {
        connectionMonitor = Executors.newSingleThreadScheduledExecutor();
        
        connectionMonitor.scheduleWithFixedDelay(() -> {
            // Monitor age independente de sessões — SSE deve estar sempre ativo
            if (isConnected && lastMessageTime > 0) {
                long timeSinceLastMessage = System.currentTimeMillis() - lastMessageTime;
                
                if (timeSinceLastMessage > 90000) { // 90 segundos
                    log.info("⚠️ Timeout OPMET - reconectando...");
                    
                    // Cancela conexão atual
                    if (sseTask != null) sseTask.cancel(true);
                    if (activeConnection != null) activeConnection.disconnect();
                    
                    // Faz novo login e reconecta
                    if (login()) {
                        lastMessageTime = System.currentTimeMillis();
                        startSSE();
                    } else {
                        isConnected = false;
                        broadcastMessage(createStatusMessage("reconnect_failed", false));
                    }
                }
            }
        }, 30, 30, TimeUnit.SECONDS);
    }
    
    private static String createStatusMessage(String type, boolean connected) {
        return String.format(
            "{\"type\":\"status\",\"status\":\"%s\",\"connected\":%b,\"timestamp\":\"%s\",\"sessions\":%d}",
            type, connected, LocalDateTime.now().toString(), sessions.size()
        );
    }
    
    private static String createDataMessage(String data) {
        boolean isSB = data.matches("(?s).*\\bSIGMET\\b.*\\bSB[A-Z]{2}\\b.*") || data.matches("(?s).*\\bSB[A-Z]{2}\\s+SIGMET\\b.*");
        
        String messageWithTimestamp = String.format("[%s] %s", 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), data);
        
        redisAppendMessage(messageWithTimestamp); // compartilhado entre containers

        // SPECI de aeródromo brasileiro → atualiza cache imediatamente
        if (data.contains("SPECI ")) {
            String[] tokens = data.split(" ");
            for (int i = 0; i < tokens.length - 1; i++) {
                if ("SPECI".equals(tokens[i])) {
                    String icao = tokens[i + 1];
                    if (icao.matches("[A-Z]{4}") && AirportConfig.getAirportSet().contains(icao)) {
                        log.info("⚡ SPECI recebido via SSE para {} — atualizando cache", icao);
                        CompletableFuture.runAsync(() -> {
                            try { RedisMetarCacheService.forceRefresh(icao, true); }
                            catch (Exception e) { log.warn("Erro ao atualizar SPECI {}: {}", icao, e.getMessage()); }
                        });
                    }
                    break;
                }
            }
        }

        // AD WRNG / WS WRNG → força refresh do hasAviso do aeródromo afetado
        if (data.contains("AD WRNG") || data.contains("WS WRNG")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(S[A-Z]{3})\\b").matcher(data);
            while (m.find()) {
                String icao = m.group(1);
                if (AirportConfig.getAirportSet().contains(icao)) {
                    log.info("⚡ AD/WS WRNG recebido via SSE para {} — atualizando cache", icao);
                    CompletableFuture.runAsync(() -> {
                        try { RedisMetarCacheService.forceRefresh(icao, true); }
                        catch (Exception e) { log.warn("Erro ao atualizar WRNG {}: {}", icao, e.getMessage()); }
                    });
                }
            }
        }
        
        return String.format(
            "{\"type\":\"data\",\"message\":\"%s\",\"highlight\":%b,\"timestamp\":\"%s\"}",
            escapeJson(data), isSB, LocalDateTime.now().toString()
        );
    }
    
    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    private static void broadcastMessage(String message) {
        broadcastLocalOnly(message);
        // publica para outras instâncias via Redis pub/sub — tagged com origin para evitar loop
        try (Jedis j = RedisMetarCacheService.getJedis()) {
            if (j != null) {
                String tagged = message.endsWith("}")
                    ? message.substring(0, message.length()-1) + ",\"origin\":\"" + INSTANCE_ID + "\"}"
                    : message;
                j.publish(REDIS_BROADCAST, tagged);
            }
        } catch (Exception e) { /* Redis opcional */ }
    }

    private static void broadcastLocalOnly(String message) {
        if (sessions.isEmpty()) return;
        if (testingMode && !message.contains("\"type\":\"data\"")) return;
        Iterator<OpmetSession> iterator = sessions.iterator();
        while (iterator.hasNext()) {
            OpmetSession session = iterator.next();
            if (!sendToSession(session, message)) iterator.remove();
        }
    }
    
    private static boolean sendToSession(OpmetSession session, String message) {
        try {
            session.sendMessage(message);
            return true;
        } catch (Exception e) {
            log.warn(String.valueOf("❌ Erro ao enviar para sessão OPMET: " + e.getMessage()));
            return false;
        }
    }
    
    // Interface para sessões WebSocket
    public interface OpmetSession {
        void sendMessage(String message) throws Exception;
        String getSessionId();
    }
    
    // Métodos para testes e status
    public static String getCurrentToken() {
        return currentToken;
    }
    
    public static int getSessionCount() {
        return sessions.size();
    }
    
    public static long getLastMessageTime() {
        return lastMessageTime;
    }
    
    private static volatile boolean testingMode = false;

    public static void resetTestingMode() {
        testingMode = false;
        sessions.clear();
    }

    public static void setTokenForTesting(String token) {
        currentToken = token;
        testingMode = true;
    }
    
    public static void simulateMessageForTesting(String message) {
        log.info(String.valueOf("🧪 Simulando mensagem: " + message));
        String json = createDataMessage(message);
        broadcastMessage(json);
    }
    
    public static String getLastMessage() {
        String[] messages = getRecentMessages();
        return messages.length > 0 ? messages[messages.length - 1] : null;
    }
    
    // Método para configurar parâmetro IWXXM
    public static void setIwxxmParameter(boolean useIwxxm) {
        useIwxxmParameter = useIwxxm;
        log.info(String.valueOf("🔧 Parâmetro IWXXM configurado: " + useIwxxm));
    }
}
