package com.pocsigmet.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pocsigmet.HSVConvectionHandler;
import com.pocsigmet.util.ImageTransparencyProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ImageProcessingService {
    
    private static final Logger logger = LoggerFactory.getLogger(ImageProcessingService.class);
    
    @Autowired
    private ImageTransparencyProcessor imageTransparencyProcessor;
    
    @Value("${app.processing.image-processing-enabled:true}")
    private boolean imageProcessingEnabled;
    
    @Value("${app.processing.hsv-analysis-enabled:true}")
    private boolean hsvAnalysisEnabled;
    
    private static final String DATA_PATH = "./data";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    
    private String currentImagePath;
    
    @Cacheable(value = "convectionAnalysis", key = "'latest'")
    public Map<String, Object> getLatestConvectionAnalysis() {
        if (!imageProcessingEnabled || !hsvAnalysisEnabled) {
            return Map.of("error", "Image processing disabled");
        }
        
        try {
            // Buscar a imagem mais recente
            String latestImage = findLatestConvectionImage();
            if (latestImage == null) {
                return Map.of("error", "No convection images found");
            }
            
            return processConvectionImage(latestImage);
            
        } catch (Exception e) {
            logger.error("Erro na análise de convecção: {}", e.getMessage());
            return Map.of("error", "Processing failed: " + e.getMessage());
        }
    }
    
    public Map<String, Object> processConvectionImage(String imagePath) {
        if (!imageProcessingEnabled) {
            return Map.of("error", "Image processing disabled");
        }
        
        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                return Map.of("error", "Image file not found: " + imagePath);
            }
            
            // Processar com HSV
            List<HSVConvectionHandler.HSVRegion> regions = HSVConvectionHandler.detectConvectionHSV(imagePath);
            
            // Converter para formato JSON amigável
            List<Map<String, Object>> processedRegions = regions.stream()
                .map(this::convertRegionToMap)
                .collect(Collectors.toList());
            
            // Estatísticas
            Map<String, Integer> severityCount = regions.stream()
                .collect(Collectors.groupingBy(
                    r -> r.severity,
                    Collectors.summingInt(r -> 1)
                ));
            
            int totalPixels = regions.stream()
                .mapToInt(r -> r.pixelCount)
                .sum();
            
            return Map.of(
                "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMAT),
                "imagePath", imagePath,
                "regions", processedRegions,
                "statistics", Map.of(
                    "totalRegions", regions.size(),
                    "totalPixels", totalPixels,
                    "severityDistribution", severityCount
                ),
                "status", "success"
            );
            
        } catch (Exception e) {
            logger.error("Erro ao processar imagem {}: {}", imagePath, e.getMessage());
            return Map.of("error", "Failed to process image: " + e.getMessage());
        }
    }
    
    private String findLatestConvectionImage() {
        try {
            Path dataDir = Paths.get(DATA_PATH);
            if (!Files.exists(dataDir)) {
                return null;
            }
            
            // Buscar por imagens de convecção (padrão: convection_hsv_*.png)
            Optional<Path> latestFile = Files.list(dataDir)
                .filter(path -> {
                    String fileName = path.getFileName().toString().toLowerCase();
                    return fileName.startsWith("convection_hsv_") && fileName.endsWith(".png");
                })
                .max(Comparator.comparing(path -> {
                    try {
                        return Files.getLastModifiedTime(path);
                    } catch (Exception e) {
                        return java.nio.file.attribute.FileTime.fromMillis(0);
                    }
                }));
            
            return latestFile.map(Path::toString).orElse(null);
            
        } catch (Exception e) {
            logger.error("Erro ao buscar imagem mais recente: {}", e.getMessage());
            return null;
        }
    }
    
    private Map<String, Object> convertRegionToMap(HSVConvectionHandler.HSVRegion region) {
        return Map.of(
            "x", region.x,
            "y", region.y,
            "width", region.width,
            "height", region.height,
            "severity", region.severity,
            "pixelCount", region.pixelCount,
            "area", region.width * region.height
        );
    }
    
    public List<String> getAvailableImages() {
        try {
            Path dataDir = Paths.get(DATA_PATH);
            if (!Files.exists(dataDir)) {
                return Collections.emptyList();
            }
            
            return Files.list(dataDir)
                .filter(path -> {
                    String fileName = path.getFileName().toString().toLowerCase();
                    return fileName.endsWith(".png") || fileName.endsWith(".jpg");
                })
                .map(Path::toString)
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            logger.error("Erro ao listar imagens disponíveis: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    public String getLatestImagePath() {
        if (currentImagePath != null) {
            return currentImagePath;
        }
        return findLatestConvectionImage();
    }
    
    public String createTransparentVersion() {
        String originalPath = getLatestImagePath();
        if (originalPath == null) {
            throw new RuntimeException("Nenhuma imagem REDEMET encontrada");
        }
        
        String transparentPath = originalPath.replace(".png", "_transparent.png");
        
        try {
            Files.copy(Paths.get(originalPath), Paths.get(transparentPath), 
                      StandardCopyOption.REPLACE_EXISTING);
            
            imageTransparencyProcessor.removeBackground(transparentPath);
            
            return transparentPath;
        } catch (IOException e) {
            throw new RuntimeException("Erro ao criar versão transparente", e);
        }
    }
}
