package com.pocsigmet.scheduler;

import com.pocsigmet.HSVAnalysisService;
import com.pocsigmet.ImageDownloadService;
import com.pocsigmet.RedisMetarCacheService;
import com.pocsigmet.grib2.Grib2Downloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class ImageAndGrib2Scheduler {

    private static final Logger log = LoggerFactory.getLogger(ImageAndGrib2Scheduler.class);

    private final ImageDownloadService downloadService;
    private final com.pocsigmet.HSVAnalysisService hsvService;
    private final Grib2Downloader grib2Downloader;

    private final com.pocsigmet.RedemetMetarClient metarClient;

    public ImageAndGrib2Scheduler(ImageDownloadService downloadService,
                                   com.pocsigmet.HSVAnalysisService hsvService,
                                   Grib2Downloader grib2Downloader,
                                   com.pocsigmet.RedemetMetarClient metarClient) {
        this.downloadService = downloadService;
        this.hsvService = hsvService;
        this.grib2Downloader = grib2Downloader;
        this.metarClient = metarClient;
    }

    private boolean acquireLock(String key, int ttlSeconds) {
        try (Jedis j = RedisMetarCacheService.getJedis()) {
            if (j == null) return true; // sem Redis, executa sempre
            return "OK".equals(j.set(key, "1", new SetParams().nx().ex(ttlSeconds)));
        } catch (Exception e) { return true; }
    }

    private static final int WARMUP_BATCH_SIZE = 5;

    @Scheduled(fixedDelay = 180_000, initialDelay = 5_000)
    public void warmupMetarCache() {
        java.util.List<Object[]> airports = com.pocsigmet.AirportConfig.getAirports();
        if (airports == null || airports.isEmpty()) return;
        log.info("🔄 Warmup METAR bulk: {} aeródromos", airports.size());
        try {
            java.util.List<String> icaos = airports.stream().map(a -> (String) a[0]).collect(java.util.stream.Collectors.toList());
            java.util.Map<String, String> metars = metarClient.getLatestMetarBulk(icaos);
            String allAvisos = metarClient.getAvisoAerodromo();
            for (String icao : icaos) {
                try { com.pocsigmet.RedisMetarCacheService.saveFromBulk(icao, metars.get(icao), allAvisos); }
                catch (Exception e) { log.debug("saveFromBulk {} erro: {}", icao, e.getMessage()); }
            }
            log.info("✅ Warmup METAR bulk concluído");
        } catch (Exception e) { log.warn("⚠️ Warmup bulk erro: {}", e.getMessage()); }
    }

    @Scheduled(fixedDelay = 180_000, initialDelay = 10_000)
    public void refreshAvisosAerodromo() {
        if (!acquireLock("lock:avisos:aerodromo", 170)) return;
        try { metarClient.getAvisoAerodromo(); }
        catch (Exception e) { log.warn("❌ Erro avisos aeródromo: {}", e.getMessage()); }
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 15_000)
    public void runHSVAnalysis() {
        try { hsvService.runHSVAnalysis(); }
        catch (Exception e) { log.warn("❌ Erro HSV: {}", e.getMessage()); }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void downloadImages() {        if (!acquireLock("lock:download", 120)) {
            log.info("⏭️ Download já em execução em outro container, pulando...");
            return;
        }
        try {
            log.info("🔄 Download imagens satélite...");
            downloadService.downloadImages();
            log.info("✅ Download concluído");
            hsvService.runHSVAnalysis();
        } catch (Exception e) {            log.warn("❌ Erro no download: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
    public void watchdog() {
        String hoje = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        File[] imgs = new File("data").listFiles((d, n) -> n.startsWith("canal16_" + hoje) && n.endsWith(".jpg"));
        int count = imgs != null ? imgs.length : 0;
        log.info("🐕 Watchdog: {} imagens de hoje", count);
        if (count < 5) {
            log.warn("⚠️ Poucas imagens! Forçando download...");
            downloadService.downloadImages();
        }
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 0)
    public void downloadGrib2() {
        if (!acquireLock("lock:grib2", 3600)) {
            log.info("⏭️ GRIB2 já em execução em outro container, pulando...");
            return;
        }
        try {
            log.info("🔄 Download GRIB2...");
            String file = grib2Downloader.downloadGrib2FullFile("f000");
            if (file != null && new File(file).exists()) {
                log.info("✅ GRIB2 baixado: {}", file);
            } else {
                log.warn("❌ Falha no download GRIB2");
            }
        } catch (Exception e) {
            log.warn("❌ Erro no GRIB2: {}", e.getMessage());
        }
    }
}
