package com.pocsigmet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * WebSocket simples para notificar novas imagens realçada
 */
public class RealcadaWebSocket {
    private static final Logger log = LoggerFactory.getLogger(RealcadaWebSocket.class);
    
    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private static WatchService watchService;
    private static boolean isWatching = false;
    
    public static void addSession(WebSocketSession session) {
        sessions.add(session);
        log.info("🔌 Nova conexão WebSocket: " + sessions.size() + " conectados");
        
        // Iniciar monitoramento se for a primeira conexão
        if (!isWatching) {
            startDirectoryWatcher();
        }
    }
    
    public static void removeSession(WebSocketSession session) {
        sessions.remove(session);
        log.info("🔌 Conexão WebSocket removida: " + sessions.size() + " conectados");
    }
    
    private static void startDirectoryWatcher() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path dataDir = Paths.get("data");
            
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            
            dataDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            isWatching = true;
            
            // Thread para monitorar o diretório
            CompletableFuture.runAsync(() -> {
                log.info("👁️ Iniciando monitoramento do diretório data/");
                
                while (isWatching) {
                    try {
                        WatchKey key = watchService.take();
                        
                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                                Path fileName = (Path) event.context();
                                String name = fileName.toString();
                                
                                // Verificar se é uma nova imagem realçada
                                if (name.startsWith("realcada_") && name.endsWith(".png")) {
                                    log.info(String.valueOf("🆕 Nova imagem detectada: " + name));
                                    notifyNewImage(name);
                                }
                            }
                        }
                        
                        key.reset();
                        
                    } catch (InterruptedException e) {
                        log.info("⏹️ Monitoramento interrompido");
                        break;
                    } catch (Exception e) {
                        log.warn(String.valueOf("❌ Erro no monitoramento: " + e.getMessage()));
                    }
                }
            });
            
        } catch (Exception e) {
            log.warn(String.valueOf("❌ Erro ao iniciar monitoramento: " + e.getMessage()));
        }
    }
    
    private static void notifyNewImage(String filename) {
        if (sessions.isEmpty()) return;
        
        try {
            // Extrair timestamp do nome do arquivo
            String dateStr = filename.substring(9, 21); // YYYYMMDDHHMM
            String year = dateStr.substring(0, 4);
            String month = dateStr.substring(4, 6);
            String day = dateStr.substring(6, 8);
            String hour = dateStr.substring(8, 10);
            String minute = dateStr.substring(10, 12);
            String timestamp = year + "-" + month + "-" + day + "T" + hour + ":" + minute + ":00Z";
            
            // Criar mensagem JSON
            String message = String.format(
                "{\"type\":\"new_realcada\",\"filename\":\"%s\",\"url\":\"/data/%s\",\"timestamp\":\"%s\",\"generated_at\":\"%s\"}",
                filename,
                filename,
                timestamp,
                LocalDateTime.now().toString()
            );
            
            // Enviar para todas as sessões conectadas
            Iterator<WebSocketSession> iterator = sessions.iterator();
            while (iterator.hasNext()) {
                WebSocketSession session = iterator.next();
                try {
                    session.sendMessage(message);
                    log.info(String.valueOf("📤 Notificação enviada: " + filename));
                } catch (Exception e) {
                    log.warn(String.valueOf("❌ Erro ao enviar para sessão: " + e.getMessage()));
                    iterator.remove(); // Remove sessão inválida
                }
            }
            
        } catch (Exception e) {
            log.warn(String.valueOf("❌ Erro ao notificar nova imagem: " + e.getMessage()));
        }
    }
    
    public static void stop() {
        isWatching = false;
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (Exception e) {
            log.warn(String.valueOf("❌ Erro ao parar monitoramento: " + e.getMessage()));
        }
    }
    
    // Interface simples para WebSocket Session
    public interface WebSocketSession {
        void sendMessage(String message) throws Exception;
    }
}
