package com.pocsigmet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

public class HSVNoOverlap {
    private static final Logger log = LoggerFactory.getLogger(HSVNoOverlap.class);
    
    static class HSVRegion {
        int x, y, width, height;
        String severity;
        int pixelCount;
        
        HSVRegion(int x, int y, int width, int height, String severity, int pixelCount) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.severity = severity; this.pixelCount = pixelCount;
        }
        
        boolean overlaps(HSVRegion other) {
            return !(x + width <= other.x || other.x + other.width <= x ||
                     y + height <= other.y || other.y + other.height <= y);
        }
    }
    
    public static void main(String[] args) {
        try {
            String imagePath = getMostRecentCanal16Image();
            log.warn(String.valueOf("DEBUG HSV NoOverlap: Usando imagem: " + imagePath));
            
            BufferedImage image = ImageIO.read(new File(imagePath));
            
            // USAR EXATAMENTE O MESMO ALGORITMO DO ORIGINAL (mas com janelas pequenas)
            List<HSVRegion> regions = detectHSVRegionsOriginal(image);
            
            log.warn("HSV NoOverlap: " + regions.size() + " quadrados pequenos sem sobreposição");
            
            // Gerar rasters
            generateRasters(image, regions);
            
            // Gerar GeoJSON
            String geoJSON = regionsToGeoJSON(regions, image.getWidth(), image.getHeight());
            // log.info(String.valueOf(geoJSON)); // Removido para não poluir logs
            
        } catch (Exception e) {
            e.printStackTrace();
            log.info("{\"error\":\"" + e.getMessage() + "\",\"features\":[],\"total\":0}");
        }
    }
    
    // COPIADO EXATAMENTE DO HSVConvectionHandler.java
    private static String classifyPixelHSV(Color color) {
        float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsv[0] * 360;        // 0-360
        float saturation = hsv[1] * 100; // 0-100
        float brightness = hsv[2] * 100; // 0-100
        
        // Filtrar pixels muito escuros ou sem saturação
        if (brightness < 20 || saturation < 30) {
            return null;
        }
        
        // Classificar por faixas de cor HSV
        if ((hue >= 0 && hue <= 15) || (hue >= 345 && hue <= 360)) {
            return "SEVERA";    // Vermelho
        } else if (hue >= 270 && hue <= 330) {
            return "FORTE";     // Roxo/Magenta - CB FORTE (-80 a -90°C)
        } else if (hue >= 45 && hue <= 75) {
            return "FORTE";     // Amarelo - também FORTE
        } else if (hue >= 90 && hue <= 150) {
            return "MODERADA";  // Verde
        }
        
        return null;
    }
    
    // USAR JANELAS FIXAS PEQUENAS EM VEZ DE FLOOD FILL
    private static List<HSVRegion> detectHSVRegionsOriginal(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        List<HSVRegion> regions = new ArrayList<>();
        
        // Janelas pequenas fixas (sem sobreposição)
        int windowSize = 12; // Quadrados bem pequenos
        int step = 14; // Separação menor (era 16, agora 14 - gap de apenas 2 pixels)
        
        for (int y = 0; y < height - windowSize; y += step) {
            for (int x = 0; x < width - windowSize; x += step) {
                
                // Contar pixels de cada severidade na janela
                int severaCount = 0;
                int forteCount = 0;
                int moderadaCount = 0;
                
                for (int dy = 0; dy < windowSize; dy++) {
                    for (int dx = 0; dx < windowSize; dx++) {
                        Color pixel = new Color(image.getRGB(x + dx, y + dy));
                        String severity = classifyPixelHSV(pixel);
                        
                        if ("SEVERA".equals(severity)) {
                            severaCount++;
                        } else if ("FORTE".equals(severity)) {
                            forteCount++;
                        } else if ("MODERADA".equals(severity)) {
                            moderadaCount++;
                        }
                    }
                }
                
                // Determinar severidade da janela (precisa de pelo menos 10% dos pixels)
                int totalPixels = windowSize * windowSize;
                int threshold = totalPixels / 10;
                
                if (severaCount > threshold) {
                    regions.add(new HSVRegion(x, y, windowSize, windowSize, "SEVERA", severaCount));
                } else if (forteCount > threshold) {
                    regions.add(new HSVRegion(x, y, windowSize, windowSize, "FORTE", forteCount));
                } else if (moderadaCount > threshold) {
                    regions.add(new HSVRegion(x, y, windowSize, windowSize, "MODERADA", moderadaCount));
                }
            }
        }
        
        log.warn("HSV NoOverlap: Detectadas " + regions.size() + " janelas pequenas");
        return regions;
    }
    
    // COPIADO EXATAMENTE DO ORIGINAL
    private static HSVRegion floodFillHSV(BufferedImage image, boolean[][] visited, int startX, int startY, String severity) {
        Stack<Point> stack = new Stack<>();
        stack.push(new Point(startX, startY));
        
        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;
        int pixelCount = 0;
        
        while (!stack.isEmpty()) {
            Point p = stack.pop();
            int x = p.x;
            int y = p.y;
            
            if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight() || visited[y][x]) {
                continue;
            }
            
            Color pixel = new Color(image.getRGB(x, y));
            String pixelSeverity = classifyPixelHSV(pixel);
            
            if (!severity.equals(pixelSeverity)) {
                continue;
            }
            
            visited[y][x] = true;
            pixelCount++;
            
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            
            // Adicionar vizinhos 4-conectados
            stack.push(new Point(x + 1, y));
            stack.push(new Point(x - 1, y));
            stack.push(new Point(x, y + 1));
            stack.push(new Point(x, y - 1));
        }
        
        return new HSVRegion(minX, minY, maxX - minX, maxY - minY, severity, pixelCount);
    }
    
    // COPIADO EXATAMENTE DO ORIGINAL
    private static int getMinPixelsForSeverity(String severity) {
        switch (severity) {
            case "SEVERA": return 100;
            case "FORTE": return 200;
            case "MODERADA": return 300;
            default: return 100;
        }
    }
    
    // NOVA FUNÇÃO: Reduzir tamanho dos quadrados até não haver sobreposição
    private static List<HSVRegion> removeOverlaps(List<HSVRegion> regions) {
        List<HSVRegion> result = new ArrayList<>();
        
        for (HSVRegion region : regions) {
            HSVRegion adjustedRegion = new HSVRegion(region.x, region.y, region.width, region.height, region.severity, region.pixelCount);
            
            // Verificar sobreposição com regiões já processadas
            boolean needsAdjustment = true;
            int attempts = 0;
            
            while (needsAdjustment && attempts < 10 && adjustedRegion.width > 10 && adjustedRegion.height > 10) {
                needsAdjustment = false;
                
                for (HSVRegion existing : result) {
                    if (adjustedRegion.overlaps(existing)) {
                        // Reduzir tamanho do quadrado (encolher das bordas)
                        adjustedRegion.x += 2;
                        adjustedRegion.y += 2;
                        adjustedRegion.width -= 4;
                        adjustedRegion.height -= 4;
                        
                        needsAdjustment = true;
                        break;
                    }
                }
                attempts++;
            }
            
            // Só adicionar se ainda tem tamanho mínimo
            if (adjustedRegion.width > 10 && adjustedRegion.height > 10) {
                result.add(adjustedRegion);
            }
        }
        
        return result;
    }
    
    private static void generateRasters(BufferedImage original, List<HSVRegion> regions) throws IOException {
        int width = original.getWidth();
        int height = original.getHeight();
        
        generateRasterBySeverity(regions, width, height, "SEVERA", "data/convection_hsv_severa_nooverlap.png", Color.RED);
        generateRasterBySeverity(regions, width, height, "FORTE", "data/convection_hsv_forte_nooverlap.png", new Color(128, 0, 128));
        generateRasterBySeverity(regions, width, height, "MODERADA", "data/convection_hsv_moderada_nooverlap.png", Color.GREEN);
    }
    
    private static void generateRasterBySeverity(List<HSVRegion> regions, int width, int height, 
                                               String targetSeverity, String filename, Color color) throws IOException {
        BufferedImage raster = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = raster.createGraphics();
        
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(AlphaComposite.SrcOver);
        
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 180));
        
        for (HSVRegion region : regions) {
            if (region.severity.equals(targetSeverity)) {
                g2d.fillRect(region.x, region.y, region.width, region.height);
            }
        }
        
        g2d.dispose();
        ImageIO.write(raster, "PNG", new File(filename));
        log.warn(String.valueOf("Raster HSV NoOverlap " + targetSeverity + " gerado: " + filename));
    }
    
    private static String regionsToGeoJSON(List<HSVRegion> regions, int imageWidth, int imageHeight) {
        StringBuilder json = new StringBuilder();
        json.append("{\"type\":\"FeatureCollection\",\"features\":[");
        
        double minLon = -119.8125;
        double maxLon = -23.4375;
        double minLat = -53.75;
        double maxLat = 40.25;
        
        for (int i = 0; i < regions.size(); i++) {
            HSVRegion region = regions.get(i);
            
            if (i > 0) json.append(",");
            
            // Centro da região
            double centerX = region.x + region.width / 2.0;
            double centerY = region.y + region.height / 2.0;
            
            double centerLon = minLon + (centerX / imageWidth) * (maxLon - minLon);
            double centerLat = minLat + ((imageHeight - centerY) / imageHeight) * (maxLat - minLat);
            
            // Criar hexágono ao redor do centro
            double radius = 0.05; // Raio do hexágono em graus
            
            json.append("{");
            json.append("\"type\":\"Feature\",");
            json.append("\"properties\":{");
            json.append("\"severity\":\"").append(region.severity).append("\",");
            json.append("\"pixels\":").append(region.pixelCount).append(",");
            json.append("\"method\":\"HSV-Hexagon\"");
            json.append("},");
            json.append("\"geometry\":{");
            json.append("\"type\":\"Polygon\",");
            json.append("\"coordinates\":[[");
            
            // Gerar 6 pontos do hexágono
            for (int p = 0; p < 6; p++) {
                double angle = p * Math.PI / 3; // 60 graus entre pontos
                double lon = centerLon + radius * Math.cos(angle);
                double lat = centerLat + radius * Math.sin(angle);
                
                if (p > 0) json.append(",");
                json.append("[").append(lon).append(",").append(lat).append("]");
            }
            
            // Fechar polígono (primeiro ponto = último)
            double firstLon = centerLon + radius * Math.cos(0);
            double firstLat = centerLat + radius * Math.sin(0);
            json.append(",[").append(firstLon).append(",").append(firstLat).append("]");
            
            json.append("]]");
            json.append("}");
            json.append("}");
        }
        
        json.append("],");
        json.append("\"total\":").append(regions.size()).append(",");
        json.append("\"timestamp\":\"").append(java.time.Instant.now()).append("\"");
        json.append("}");
        
        return json.toString();
    }
    
    private static String getMostRecentCanal16Image() {
        File dataDir = new File("data");
        if (!dataDir.exists() || !dataDir.isDirectory()) {
            log.warn("❌ Diretório data/ não existe!");
            return null;
        }
        
        File[] files = dataDir.listFiles((dir, name) -> 
            name.startsWith("canal16_202") && name.endsWith(".jpg"));
        if (files == null || files.length == 0) {
            log.warn("❌ Nenhuma imagem canal16_202*.jpg encontrada!");
            return null;
        }
        
        // Ordenar por nome (que contém timestamp) - mais recente por último
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        
        String mostRecent = files[files.length - 1].getAbsolutePath();
        log.warn(String.valueOf("🎯 Imagem mais recente: " + mostRecent));
        return mostRecent;
    }
}
