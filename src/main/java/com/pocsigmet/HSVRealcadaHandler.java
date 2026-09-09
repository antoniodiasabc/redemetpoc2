package com.pocsigmet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.List;

public class HSVRealcadaHandler {
    private static final Logger log = LoggerFactory.getLogger(HSVRealcadaHandler.class);
    
    public static class HSVRegion {
        public int x, y, width, height;
        public String severity;
        public int pixelCount;
        
        public HSVRegion(int x, int y, int width, int height, String severity, int pixelCount) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.severity = severity;
            this.pixelCount = pixelCount;
        }
    }
    
    public static void main(String[] args) {
        log.info("🔍 HSV Realçada - Iniciando análise...");
        
        String imagePath = "data/realcada_latest.png";
        File imageFile = new File(imagePath);
        
        if (!imageFile.exists()) {
            log.warn(String.valueOf("❌ Imagem não encontrada: " + imagePath));
            return;
        }
        
        List<HSVRegion> regions = detectConvectionHSV(imagePath);
        generateOutputs(regions, imagePath);
        
        log.info("✅ HSV Realçada concluído!");
    }
    
    public static List<HSVRegion> detectConvectionHSV(String imagePath) {
        try {
            BufferedImage image = ImageIO.read(new File(imagePath));
            return analyzeImageHSV(image);
        } catch (IOException e) {
            log.warn(String.valueOf("Erro ao carregar imagem HSV Realçada: " + e.getMessage()));
            return new ArrayList<>();
        }
    }
    
    private static List<HSVRegion> analyzeImageHSV(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        List<HSVRegion> regions = new ArrayList<>();
        boolean[][] visited = new boolean[height][width];
        
        // Analisar imagem pixel por pixel
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!visited[y][x]) {
                    Color pixel = new Color(image.getRGB(x, y));
                    String severity = classifyPixelRealcada(pixel);
                    
                    if (!severity.equals("normal")) {
                        HSVRegion region = floodFill(image, visited, x, y, severity);
                        if (region.pixelCount > 50) { // Filtrar regiões muito pequenas
                            regions.add(region);
                        }
                    }
                }
            }
        }
        
        return regions;
    }
    
    private static String classifyPixelRealcada(Color pixel) {
        // Classificação específica para imagens Realçada (tons de cinza/branco)
        int r = pixel.getRed();
        int g = pixel.getGreen();
        int b = pixel.getBlue();
        
        // Converter para HSV
        float[] hsv = Color.RGBtoHSB(r, g, b, null);
        float hue = hsv[0] * 360;
        float saturation = hsv[1] * 100;
        float brightness = hsv[2] * 100;
        
        // Critérios para imagem Realçada (nuvens mais brilhantes = mais frias = mais perigosas)
        if (brightness > 85 && saturation < 20) {
            return "severa";     // Muito branco = muito frio = severo
        } else if (brightness > 70 && saturation < 30) {
            return "forte";      // Branco = frio = forte
        } else if (brightness > 55 && saturation < 40) {
            return "moderada";   // Cinza claro = moderado
        }
        
        return "normal";
    }
    
    private static HSVRegion floodFill(BufferedImage image, boolean[][] visited, int startX, int startY, String severity) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        Queue<Point> queue = new LinkedList<>();
        queue.offer(new Point(startX, startY));
        visited[startY][startX] = true;
        
        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;
        int pixelCount = 0;
        
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            pixelCount++;
            
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
            
            // Verificar vizinhos (4-conectividade)
            int[][] directions = {{0,1}, {1,0}, {0,-1}, {-1,0}};
            for (int[] dir : directions) {
                int nx = p.x + dir[0];
                int ny = p.y + dir[1];
                
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && !visited[ny][nx]) {
                    Color neighborPixel = new Color(image.getRGB(nx, ny));
                    if (classifyPixelRealcada(neighborPixel).equals(severity)) {
                        visited[ny][nx] = true;
                        queue.offer(new Point(nx, ny));
                    }
                }
            }
        }
        
        return new HSVRegion(minX, minY, maxX - minX, maxY - minY, severity, pixelCount);
    }
    
    private static void generateOutputs(List<HSVRegion> regions, String imagePath) {
        try {
            BufferedImage originalImage = ImageIO.read(new File(imagePath));
            
            // Gerar imagens por severidade
            String[] severities = {"moderada", "forte", "severa"};
            Color[] colors = {Color.YELLOW, Color.ORANGE, Color.RED};
            
            for (int i = 0; i < severities.length; i++) {
                generateSeverityImage(originalImage, regions, severities[i], colors[i]);
            }
            
            // Gerar JSON
            generateJSON(regions);
            
            log.info(String.valueOf("📊 Regiões encontradas: " + regions.size()));
            
        } catch (IOException e) {
            log.warn(String.valueOf("Erro ao gerar outputs: " + e.getMessage()));
        }
    }
    
    private static void generateSeverityImage(BufferedImage original, List<HSVRegion> regions, String severity, Color color) {
        try {
            BufferedImage output = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = output.createGraphics();
            g2d.drawImage(original, 0, 0, null);
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(2));
            
            for (HSVRegion region : regions) {
                if (region.severity.equals(severity)) {
                    g2d.drawRect(region.x, region.y, region.width, region.height);
                }
            }
            
            g2d.dispose();
            ImageIO.write(output, "PNG", new File("data/realcada_hsv_" + severity + ".png"));
            
        } catch (IOException e) {
            log.warn(String.valueOf("Erro ao gerar imagem " + severity + ": " + e.getMessage()));
        }
    }
    
    private static void generateJSON(List<HSVRegion> regions) {
        try (FileWriter writer = new FileWriter("data/realcada_hsv.json")) {
            writer.write("{\n");
            writer.write("  \"type\": \"FeatureCollection\",\n");
            writer.write("  \"features\": [\n");
            
            for (int i = 0; i < regions.size(); i++) {
                HSVRegion region = regions.get(i);
                writer.write("    {\n");
                writer.write("      \"type\": \"Feature\",\n");
                writer.write("      \"properties\": {\n");
                writer.write("        \"severity\": \"" + region.severity + "\",\n");
                writer.write("        \"pixelCount\": " + region.pixelCount + "\n");
                writer.write("      },\n");
                writer.write("      \"geometry\": {\n");
                writer.write("        \"type\": \"Polygon\",\n");
                writer.write("        \"coordinates\": [[\n");
                writer.write("          [" + region.x + ", " + region.y + "],\n");
                writer.write("          [" + (region.x + region.width) + ", " + region.y + "],\n");
                writer.write("          [" + (region.x + region.width) + ", " + (region.y + region.height) + "],\n");
                writer.write("          [" + region.x + ", " + (region.y + region.height) + "],\n");
                writer.write("          [" + region.x + ", " + region.y + "]\n");
                writer.write("        ]]\n");
                writer.write("      }\n");
                writer.write("    }");
                if (i < regions.size() - 1) writer.write(",");
                writer.write("\n");
            }
            
            writer.write("  ]\n");
            writer.write("}\n");
            
        } catch (IOException e) {
            log.warn(String.valueOf("Erro ao gerar JSON: " + e.getMessage()));
        }
    }
}
