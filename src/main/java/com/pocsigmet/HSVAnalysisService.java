package com.pocsigmet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@org.springframework.stereotype.Service
public class HSVAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(HSVAnalysisService.class);
    private final java.util.concurrent.atomic.AtomicReference<String> lastProcessed = new java.util.concurrent.atomic.AtomicReference<>("");

    public void runHSVAnalysis() {
        LocalTime analysisTime = LocalTime.now();
        File dataDir = new File("data");
        File[] canal16Files = dataDir.listFiles((dir, name) -> name.startsWith("canal16_202") && name.endsWith(".jpg"));
        String originalImageName = "canal16_latest.jpg";
        String imageTimestamp = "N/A";

        if (canal16Files != null && canal16Files.length > 0) {
            java.util.Arrays.sort(canal16Files, (a, b) -> b.getName().compareTo(a.getName()));
            File latestImage = canal16Files[0];
            originalImageName = latestImage.getName();

            try {
                if (originalImageName.equals(lastProcessed.get())) {
                    log.info("⏭️ HSV já processado para " + originalImageName + " — pulando.");
                    return;
                }
            } catch (Exception e) {}

            try {
                BufferedImage image = ImageIO.read(latestImage);
                if (image != null) {
                    ImageIO.write(image, "JPEG", new File("data/canal16_latest.jpg"));
                    log.info("📋 Copiada imagem mais recente: " + originalImageName);
                }
            } catch (Exception e) { log.warn("❌ Erro ao copiar imagem: " + e.getMessage()); }

            imageTimestamp = java.time.Instant.ofEpochMilli(latestImage.lastModified())
                .atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        log.info("🔍 [" + analysisTime + "] Executando análise HSV...");
        try {
            HSVConvectionHandler.main(new String[]{});
            HSVNoOverlap.main(new String[]{});
            saveHSVMetadata(analysisTime, imageTimestamp, originalImageName);
            lastProcessed.set(originalImageName);
            java.nio.file.Files.writeString(java.nio.file.Paths.get("/app/data/hsv_updated.txt"),
                String.valueOf(System.currentTimeMillis()));
            log.info("✅ Análise HSV concluída!");
        } catch (Exception e) {
            log.warn("❌ Erro na análise HSV: " + e.getMessage());
        }
    }

    private void saveHSVMetadata(LocalTime analysisTime, String imageTimestamp, String originalImageName) {
        try {
            String metadata = String.format(
                "{\"analysis_time\":\"%s\",\"analysis_date\":\"%s\",\"base_image\":\"%s\",\"image_timestamp\":\"%s\",\"label\":\"HSV %s - %s\"}",
                analysisTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                originalImageName, imageTimestamp,
                analysisTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                originalImageName.replace("canal16_", "").replace(".jpg", ""));
            Files.write(Paths.get("data/hsv_metadata.json"), metadata.getBytes());
            log.info("📋 Metadados HSV salvos.");
        } catch (Exception e) { log.warn("❌ Erro ao salvar metadados HSV: " + e.getMessage()); }
    }
}
