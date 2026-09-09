package com.pocsigmet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.*;
import java.util.*;

/**
 * Carrega a lista de aeroportos de /app/data/airports.json (volume externo).
 * Se o arquivo não existir, usa o array estático de PocSigmetApplication como fallback.
 * Para adicionar/remover aeroportos: edite airports.json e reinicie os containers.
 */
public class AirportConfig {
    private static final Logger log = LoggerFactory.getLogger(AirportConfig.class);
    private static final String AIRPORTS_FILE = "/app/data/config/airports.json";
    private static final ObjectMapper mapper = new ObjectMapper();

    private static volatile List<Object[]> airports;
    private static volatile Set<String> airportSet;

    static {
        reload();
        // Monitora mudanças no arquivo e recarrega automaticamente
        Thread watcher = new Thread(() -> {
            try {
                Path dir = java.nio.file.Paths.get("/app/data");
                WatchService ws = dir.getFileSystem().newWatchService();
                dir.register(ws, java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY);
                while (true) {
                    WatchKey key = ws.take();
                    for (java.nio.file.WatchEvent<?> ev : key.pollEvents()) {
                        if (ev.context().toString().equals("airports.json")) {
                            Thread.sleep(200); // aguarda escrita terminar
                            reload();
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                log.warn("WatchService airports.json encerrado: {}", e.getMessage());
            }
        });
        watcher.setDaemon(true);
        watcher.start();
    }

    public static void reload() {
        File f = new File(AIRPORTS_FILE);
        if (f.exists()) {
            try {
                JsonNode root = mapper.readTree(f);
                List<Object[]> list = new ArrayList<>();
                for (JsonNode node : root) {
                    list.add(new Object[]{
                        node.get("icao").asText(),
                        node.get("nome").asText(),
                        node.get("lat").asDouble(),
                        node.get("lon").asDouble()
                    });
                }
                airports = Collections.unmodifiableList(list);
                Set<String> set = new HashSet<>();
                for (Object[] a : airports) set.add((String) a[0]);
                airportSet = Collections.unmodifiableSet(set);
                log.info("✅ airports.json carregado: {} aeroportos", airports.size());
                return;
            } catch (Exception e) {
                log.warn("⚠️ Erro ao ler airports.json, usando fallback hardcoded: {}", e.getMessage());
            }
        } else {
            log.info("ℹ️ airports.json não encontrado em {}, usando fallback hardcoded", AIRPORTS_FILE);
        }
        // fallback — airports.json não encontrado
        log.warn("⚠️ Usando lista de aeroportos vazia — airports.json não encontrado em {}", AIRPORTS_FILE);
        airports = Collections.emptyList();
        airportSet = Collections.emptySet();
    }

    public static List<Object[]> getAirports() { return airports; }
    public static Set<String> getAirportSet()  { return airportSet; }
}
