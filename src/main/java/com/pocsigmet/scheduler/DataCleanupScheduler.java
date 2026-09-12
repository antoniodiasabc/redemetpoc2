package com.pocsigmet.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class DataCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DataCleanupScheduler.class);

    @Value("${app.data.path:./data}")
    private String dataPath;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void cleanupOldData() {
        Instant cutoff3h = Instant.now().minus(3, ChronoUnit.HOURS);
        Instant cutoff6h = Instant.now().minus(6, ChronoUnit.HOURS);

        preserveLatestFrames(Paths.get(dataPath), "canal16_202", 10);
        preserveLatestFrames(Paths.get(dataPath), "vis_", 8);
        preserveLatestFrames(Paths.get(dataPath), "realcada_", 8);

        // raiz /data — imagens satélite, canal16, etc → 3h
        deleteOlderThan(Paths.get(dataPath), cutoff3h, false);
        // subdir images → 3h
        deleteOlderThan(Paths.get(dataPath, "images"), cutoff3h, false);
        // subdir grib2 → 6h
        deleteOlderThan(Paths.get(dataPath, "grib2"), cutoff6h, false);

        logger.info("✅ Cleanup concluído");
    }

    /** Marca os N arquivos mais recentes com prefixo como importantes (toca o lastModified) para não serem deletados */
    private void preserveLatestFrames(Path dir, String prefix, int keep) {
        if (!Files.exists(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, prefix + "*.{jpg,png}")) {
            java.util.List<Path> files = new java.util.ArrayList<>();
            stream.forEach(files::add);
            files.sort(java.util.Comparator.comparing(Path::getFileName).reversed());
            files.stream().limit(keep).forEach(f -> f.toFile().setLastModified(System.currentTimeMillis()));
        } catch (IOException e) {
            logger.warn("preserveLatestFrames erro {}: {}", dir, e.getMessage());
        }
    }

    /** @param recurse false = só arquivos diretos do diretório, sem descer subpastas */
    private void deleteOlderThan(Path dir, Instant cutoff, boolean recurse) {
        if (!Files.exists(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                if (Files.isDirectory(file)) {
                    if (recurse) deleteOlderThan(file, cutoff, true);
                    continue;
                }
                if (isImportantFile(file.getFileName().toString())) continue;
                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        try { Files.delete(file); logger.debug("🗑 Removido: {}", file); }
                        catch (IOException ignored) {} // já deletado pelo outro container
                    }
            }
        } catch (IOException e) {
            logger.error("Erro na limpeza de {}: {}", dir, e.getMessage());
        }
    }

    private boolean isImportantFile(String name) {
        return name.equals("hsv_metadata.json")
            || name.equals("portainer.db")
            || name.startsWith("wind_cache_")
            || name.endsWith("_latest.png")
            || name.endsWith("_latest.jpg");
    }
}
