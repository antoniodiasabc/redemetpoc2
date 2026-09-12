package com.pocsigmet.grib2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class Grib2CachePreloader {
    private static final Logger log = LoggerFactory.getLogger(Grib2CachePreloader.class);

    @Autowired
    private WindBarbService windBarbService;

    private static final List<String> LEVELS = Arrays.asList(
        "surface", "fl050", "fl100", "fl180", "fl240", "fl300", "fl390", "fl450", "fl530"
    );

    private static final String GRIB2_DIR = "data/grib2/";

    /** Dispara extração assíncrona de um nível — não bloqueia nada */
    @Async
    public void preloadLevel(String level) {
        try {
            windBarbService.getWindBarbs(level);
            System.out.printf("✅ Cache %s atualizado%n", level);
        } catch (Exception e) {
            System.err.printf("❌ Erro cache %s: %s%n", level, e.getMessage());
        }
    }

    /** Na inicialização, dispara cada nível em thread separada */
    @PostConstruct
    public void preloadOnStartup() {
        log.info("🚀 Iniciando pré-carregamento assíncrono GRIB2");
        ExecutorService exec = Executors.newFixedThreadPool(3);
        exec.submit(() -> {
            try {
                File[] files = new File(GRIB2_DIR).listFiles((d, n) -> n.endsWith(".grib2"));
                if (files != null && files.length > 0) {
                    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    String gribFile = GRIB2_DIR + files[0].getName();
                    log.info("📦 Extraindo cache GRIB2 de: {}", files[0].getName());
                    for (String level : LEVELS) {
                        try {
                            windBarbService.extractAndCache(gribFile, level);
                            log.info("✅ Cache {} atualizado", level);
                        } catch (Exception e) {
                            log.warn("❌ Erro cache {}: {}", level, e.getMessage());
                        }
                    }
                } else {
                    log.warn("⚠️ Nenhum GRIB2 encontrado em {} — aguardando download", GRIB2_DIR);
                }
            } catch (Exception e) {
                log.warn("❌ Erro no pré-carregamento GRIB2: {}", e.getMessage());
            }
        });
        exec.shutdown();
        startGrib2Watcher();
    }

    /** WatchService: detecta novo arquivo .grib2 e dispara extração */
    private void startGrib2Watcher() {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "grib2-watcher");
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            try {
                Path dir = Paths.get(GRIB2_DIR);
                if (!dir.toFile().exists()) dir.toFile().mkdirs();
                WatchService watcher = FileSystems.getDefault().newWatchService();
                dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
                log.info("👁️ Monitorando " + GRIB2_DIR + " por novos GRIB2");
                while (true) {
                    WatchKey key = watcher.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        String name = event.context().toString();
                        if (name.endsWith(".grib2")) {
                            log.info("📥 Novo GRIB2 detectado: " + name + " — disparando extração");
                            LEVELS.forEach(this::preloadLevel);
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                log.warn(String.valueOf("❌ Erro no watcher GRIB2: " + e.getMessage()));
            }
        });
    }

    /** Limpar caches antigos (executar diariamente) */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanOldCaches() {
        File dataDir = new File("data");
        if (!dataDir.exists()) return;
        File[] cacheFiles = dataDir.listFiles((dir, name) ->
            name.startsWith("wind_cache_") && name.endsWith(".json"));
        if (cacheFiles == null) return;
        long oneDayAgo = System.currentTimeMillis() - 43_200_000; // 12h
        for (File file : cacheFiles) {
            if (file.lastModified() < oneDayAgo && file.delete())
                log.info(String.valueOf("🗑️ Cache antigo removido: " + file.getName()));
        }
    }
}
