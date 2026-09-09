package com.pocsigmet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

@org.springframework.stereotype.Service
public class ImageDownloadService {

    private static final Logger log = LoggerFactory.getLogger(ImageDownloadService.class);

    public void downloadImages() {
        log.info("🔄 [" + LocalTime.now() + "] Iniciando download de imagens...");
        boolean newImageDownloaded = false;
        try {
            if (downloadVisImage()) newImageDownloaded = true;
            if (downloadRealcadaImage()) newImageDownloaded = true;
            if (downloadCanal16Images()) newImageDownloaded = true;
            downloadCptecImage();
            if (downloadRealcadaImageNew()) newImageDownloaded = true;
            if (downloadVisImageNew()) newImageDownloaded = true;
            log.info("✅ Download de imagens concluído!");
            if (newImageDownloaded) {
                log.info("🔍 Nova imagem detectada - reprocessando HSV...");
                new HSVAnalysisService().runHSVAnalysis();
            }
        } catch (Exception e) {
            log.warn("❌ Erro no download: " + e.getMessage());
        }
    }

    private boolean downloadVisImage() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        for (int i = 0; i < 6; i++) {
            LocalDateTime time = now.minusMinutes(i * 20);
            String timeStr = time.format(formatter);
            String url = String.format("https://redemet.decea.mil.br/old/satelite/%s/vis/vis_%s.png",
                time.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")), timeStr);
            if (downloadImage(url, "data/vis_latest.png")) {
                log.info("✅ VIS: " + timeStr);
                return true;
            }
        }
        return false;
    }

    private boolean downloadRealcadaImage() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        for (int i = 0; i < 18; i++) {
            LocalDateTime time = now.minusMinutes(i * 20);
            String timeStr = time.format(formatter);
            String url = String.format("https://redemet.decea.mil.br/old/satelite/%s/realcada/realcada_%s.png",
                time.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")), timeStr);
            String filename = "data/realcada_" + timeStr + ".png";
            if (downloadImage(url, filename)) {
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("data/realcada_latest.png"));
                    java.nio.file.Files.copy(java.nio.file.Paths.get(filename),
                        java.nio.file.Paths.get("data/realcada_latest.png"));
                } catch (Exception e) {}
                log.info("✅ Realçada: " + timeStr);
                return true;
            }
        }
        log.info("❌ REDEMET Realçada: Nenhuma imagem disponível");
        return false;
    }

    private boolean downloadCanal16Images() {
        new File("data").mkdirs();
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        int minutoAtual = (now.getMinute() / 10) * 10;
        LocalDateTime horaBase = now.withMinute(minutoAtual).withSecond(0).withNano(0);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM");
        DateTimeFormatter logFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        log.info("📡 [" + LocalTime.now() + "] Buscando 10+ imagens GOES-19 Canal 16...");
        int sucessos = 0;
        boolean algumSucesso = false;
        boolean latestUpdated = false;
        for (int i = 0; i < 48 && sucessos < 10; i++) {
            LocalDateTime time = horaBase.minusMinutes(i * 10);
            String timeStr = time.format(formatter);
            String dateStr = time.format(dateFormatter);
            String logTime = time.format(logFormatter);
            String url16 = String.format("https://satelite.cptec.inpe.br/repositoriogoes/goes19/goes19_web/ams_ret_ch16_baixa/%s/S11161216_%s.jpg", dateStr, timeStr);
            String filename = String.format("data/canal16_%s.jpg", timeStr);
            boolean jaExistia = new File(filename).exists();
            if (downloadImageWithTimeout(url16, filename, 30)) {
                if (!jaExistia) {
                    log.info("✅ [" + LocalTime.now() + "] SUCESSO Canal 16: " + logTime + " UTC (" + (sucessos + 1) + "/10)");
                    sucessos++; algumSucesso = true;
                    if (!latestUpdated) { try { java.nio.file.Files.copy(java.nio.file.Paths.get(filename), java.nio.file.Paths.get("data/canal16_latest.jpg"), java.nio.file.StandardCopyOption.REPLACE_EXISTING); latestUpdated = true; } catch (Exception e) {} }
                    try { SatelliteRiskGeoJSON.recebonovoArquivo(filename); } catch (Exception e) { log.warn("❌ Erro polígonos SIGMET: " + e.getMessage()); }
                }
            } else {
                String url14 = String.format("https://satelite.cptec.inpe.br/repositoriogoes/goes19/goes19_web/ams_ret_ch14_baixa/%s/S11161214_%s.jpg", dateStr, timeStr);
                if (downloadImageWithTimeout(url14, filename, 30)) {
                    if (!jaExistia) {
                        log.info("✅ [" + LocalTime.now() + "] SUCESSO Canal 14: " + logTime + " UTC (" + (sucessos + 1) + "/10)");
                        sucessos++; algumSucesso = true;
                        if (!latestUpdated) { try { java.nio.file.Files.copy(java.nio.file.Paths.get(filename), java.nio.file.Paths.get("data/canal16_latest.jpg"), java.nio.file.StandardCopyOption.REPLACE_EXISTING); latestUpdated = true; } catch (Exception e) {} }
                        try { SatelliteRiskGeoJSON.recebonovoArquivo(filename); } catch (Exception e) { log.warn("❌ Erro polígonos SIGMET: " + e.getMessage()); }
                    }
                }
            }
        }
        if (sucessos > 0) log.info("🎉 Total baixado: " + sucessos + " imagens Canal 16/14");
        else log.info("⚠️ Nenhuma imagem encontrada nas últimas 8 horas");
        return algumSucesso;
    }

    private void downloadCptecImage() {
        if (downloadImage("https://satelite.cptec.inpe.br/repositorio/goes16/goes16_web/ams_ret_ch13_baixa/latest.jpg", "data/cptec_latest.jpg"))
            log.info("✅ CPTEC: latest");
    }

    private boolean downloadRealcadaImageNew() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        DateTimeFormatter pathFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        int minuto = now.getMinute();
        now = now.withMinute(minuto >= 40 ? 40 : minuto >= 20 ? 20 : 0).withSecond(0).withNano(0);
        int baixadas = 0;
        boolean primeiraEncontrada = false;
        for (int i = 0; i < 72 && baixadas < 10; i++) {
            LocalDateTime time = now.minusMinutes(i * 20);
            String timeStr = time.format(formatter);
            String filename = "data/realcada_" + timeStr + ".png";
            if (new File(filename).exists()) continue;
            if (downloadImage(String.format("https://estatico-redemet.decea.mil.br/satelite/%s/realcada/realcada_%s.png", time.format(pathFormatter), timeStr), filename)) {
                if (!primeiraEncontrada) { try { java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("data/realcada_latest.png")); java.nio.file.Files.copy(java.nio.file.Paths.get(filename), java.nio.file.Paths.get("data/realcada_latest.png")); } catch (Exception e) {} primeiraEncontrada = true; }
                log.info("✅ Realçada NEW: " + timeStr); baixadas++;
            }
        }
        if (baixadas == 0) log.info("❌ REDEMET Realçada NEW: Nenhuma imagem nova disponível");
        return baixadas > 0;
    }

    private boolean downloadVisImageNew() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        DateTimeFormatter pathFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        int minuto = now.getMinute();
        now = now.withMinute(minuto >= 40 ? 40 : minuto >= 20 ? 20 : 0).withSecond(0).withNano(0);
        int baixadas = 0;
        for (int i = 0; i < 72 && baixadas < 10; i++) {
            LocalDateTime time = now.minusMinutes(i * 20);
            String timeStr = time.format(formatter);
            String filename = "data/vis_" + timeStr + ".png";
            if (new File(filename).exists()) continue;
            if (downloadImage(String.format("https://estatico-redemet.decea.mil.br/satelite/%s/vis/vis_%s.png", time.format(pathFormatter), timeStr), filename)) {
                if (baixadas == 0) { try { java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("data/vis_latest_new.png")); java.nio.file.Files.copy(java.nio.file.Paths.get(filename), java.nio.file.Paths.get("data/vis_latest_new.png")); } catch (Exception e) {} }
                log.info("✅ VIS NEW: " + timeStr); baixadas++;
            }
        }
        if (baixadas == 0) log.info("❌ REDEMET VIS NEW: Nenhuma imagem nova disponível");
        return baixadas > 0;
    }

    boolean downloadImage(String urlStr, String filename) {
        try {
            File existingFile = new File(filename);
            if (existingFile.exists() && existingFile.length() > 10000) return true;
            if (urlStr.contains("satelite.cptec.inpe.br")) return downloadImageViaCurl(urlStr, filename);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000); conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) return false;
            String ct = conn.getContentType();
            if (ct != null && !ct.startsWith("image/")) return false;
            BufferedImage image = ImageIO.read(conn.getInputStream());
            if (image == null) return false;
            new File(filename).getParentFile().mkdirs();
            ImageIO.write(image, filename.endsWith(".png") ? "PNG" : "JPEG", new File(filename));
            File saved = new File(filename);
            if (saved.length() < 10000) { saved.delete(); return false; }
            return true;
        } catch (Exception e) {
            log.warn("   🔌 " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    private boolean downloadImageWithTimeout(String urlStr, String filename, int timeoutSeconds) {
        try {
            return CompletableFuture.supplyAsync(() -> downloadImage(urlStr, filename))
                .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) { log.warn("   ⏰ TIMEOUT: " + timeoutSeconds + "s"); return false; }
        catch (Exception e) { log.warn("   ❌ ERRO: " + e.getMessage()); return false; }
    }

    private boolean downloadImageViaCurl(String urlStr, String filename) {
        try {
            new File(filename).getParentFile().mkdirs();
            Process p = new ProcessBuilder("curl", "-k", "-s", "--max-time", "30", "-o", filename, urlStr)
                .redirectErrorStream(true).start();
            if (p.waitFor() != 0) return false;
            File f = new File(filename);
            if (!f.exists() || f.length() < 10000) { f.delete(); return false; }
            return true;
        } catch (Exception e) { log.warn("   ❌ curl exception: " + e.getMessage()); return false; }
    }
}
