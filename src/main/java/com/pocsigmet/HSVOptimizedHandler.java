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

public class HSVOptimizedHandler {
    private static final Logger log = LoggerFactory.getLogger(HSVOptimizedHandler.class);
    
    public static class HSVPolygon {
        public List<Point> contourPoints;
        public String severity;
        public int pixelCount;
        public double area;
        
        public HSVPolygon(List<Point> contourPoints, String severity, int pixelCount) {
            this.contourPoints = contourPoints;
            this.severity = severity;
            this.pixelCount = pixelCount;
            this.area = calculateArea(contourPoints);
        }
        
        private double calculateArea(List<Point> points) {
            if (points.size() < 3) return 0;
            double area = 0;
            for (int i = 0; i < points.size(); i++) {
                Point p1 = points.get(i);
                Point p2 = points.get((i + 1) % points.size());
                area += (p1.x * p2.y - p2.x * p1.y);
            }
            return Math.abs(area) / 2.0;
        }
    }
    
    public static void main(String[] args) {
        log.info("🔥 HSV Otimizado - Iniciando análise...");
        
        String imagePath = "data/canal16_latest.jpg";
        File imageFile = new File(imagePath);
        
        if (!imageFile.exists()) {
            log.warn(String.valueOf("❌ Imagem não encontrada: " + imagePath));
            return;
        }
        
        List<HSVPolygon> polygons = detectConvectionPolygons(imagePath);
        generateOutputs(polygons, imagePath);
        
        log.info("✅ HSV Otimizado concluído!");
    }
    
    public static List<HSVPolygon> detectConvectionPolygons(String imagePath) {
        try {
            BufferedImage image = ImageIO.read(new File(imagePath));
            return analyzeImagePolygons(image);
        } catch (IOException e) {
            log.warn(String.valueOf("Erro ao carregar imagem HSV Otimizado: " + e.getMessage()));
            return new ArrayList<>();
        }
    }
    
    private static List<HSVPolygon> analyzeImagePolygons(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        List<HSVPolygon> polygons = new ArrayList<>();
        boolean[][] visited = new boolean[height][width];
        
        // Analisar imagem pixel por pixel
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!visited[y][x]) {
                    Color pixel = new Color(image.getRGB(x, y));
                    String severity = classifyPixelHSV(pixel);
                    
                    if (severity != null) {
                        HSVPolygon polygon = floodFillWithContour(image, visited, x, y, severity);
                        if (polygon != null && polygon.pixelCount >= getMinPixelsForSeverity(severity)) {
                            polygons.add(polygon);
                        }
                    }
                }
            }
        }
        
        log.info("HSV Otimizado: Detectados " + polygons.size() + " polígonos");
        return polygons;
    }
    
    private static String classifyPixelHSV(Color color) {
        float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsv[0] * 360;
        float saturation = hsv[1] * 100;
        float brightness = hsv[2] * 100;
        
        // Filtrar pixels muito escuros ou sem saturação
        if (hue >= 270 && hue <= 330) {
            if (brightness < 15 || saturation < 20) return null;
        } else {
            if (brightness < 20 || saturation < 30) return null;
        }
        
        // Classificar por faixas de cor HSV
        if ((hue >= 0 && hue <= 15) || (hue >= 345 && hue <= 360)) {
            return "severa";    // Vermelho
        } else if (hue >= 270 && hue <= 330) {
            return "forte";     // Roxo/Magenta
        } else if (hue >= 45 && hue <= 75) {
            return "forte";     // Amarelo
        } else if (hue >= 90 && hue <= 150) {
            return "moderada";  // Verde
        }
        
        return null;
    }
    
    private static HSVPolygon floodFillWithContour(BufferedImage image, boolean[][] visited, int startX, int startY, String severity) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Primeiro: flood fill para marcar todos os pixels da região
        Set<Point> regionPixels = new HashSet<>();
        Queue<Point> queue = new LinkedList<>();
        queue.offer(new Point(startX, startY));
        
        while (!queue.isEmpty()) {
            Point p = queue.poll();
            int x = p.x, y = p.y;
            
            if (x < 0 || x >= width || y < 0 || y >= height || visited[y][x]) {
                continue;
            }
            
            Color pixel = new Color(image.getRGB(x, y));
            String pixelSeverity = classifyPixelHSV(pixel);
            
            if (!severity.equals(pixelSeverity)) {
                continue;
            }
            
            visited[y][x] = true;
            regionPixels.add(new Point(x, y));
            
            // Adicionar vizinhos
            queue.offer(new Point(x+1, y));
            queue.offer(new Point(x-1, y));
            queue.offer(new Point(x, y+1));
            queue.offer(new Point(x, y-1));
        }
        
        if (regionPixels.isEmpty()) return null;
        
        // Segundo: traçar contorno usando Moore Neighborhood
        List<Point> contour = traceContour(regionPixels, width, height);
        
        // Terceiro: simplificar para máximo 10 pontos
        List<Point> simplifiedContour = simplifyPolygon(contour, 10);
        
        return new HSVPolygon(simplifiedContour, severity, regionPixels.size());
    }
    
    private static List<Point> traceContour(Set<Point> regionPixels, int width, int height) {
        // Encontrar ponto mais à esquerda e superior (ponto de início)
        Point start = null;
        for (Point p : regionPixels) {
            if (start == null || p.y < start.y || (p.y == start.y && p.x < start.x)) {
                start = p;
            }
        }
        
        if (start == null) return new ArrayList<>();
        
        List<Point> contour = new ArrayList<>();
        Point current = start;
        int direction = 0; // 0=direita, 1=baixo, 2=esquerda, 3=cima
        
        // Direções: direita, baixo, esquerda, cima
        int[] dx = {1, 0, -1, 0};
        int[] dy = {0, 1, 0, -1};
        
        do {
            contour.add(new Point(current.x, current.y));
            
            // Procurar próximo pixel do contorno
            boolean found = false;
            for (int i = 0; i < 8; i++) {
                int newDir = (direction + i) % 4;
                int nx = current.x + dx[newDir];
                int ny = current.y + dy[newDir];
                
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && 
                    regionPixels.contains(new Point(nx, ny))) {
                    current = new Point(nx, ny);
                    direction = (newDir + 3) % 4; // Virar à esquerda
                    found = true;
                    break;
                }
            }
            
            if (!found) break;
            
        } while (!current.equals(start) && contour.size() < 1000); // Limite de segurança
        
        return contour;
    }
    
    private static List<Point> simplifyPolygon(List<Point> points, int maxPoints) {
        if (points.size() <= maxPoints) return points;
        
        // Algoritmo Douglas-Peucker simplificado
        List<Point> simplified = new ArrayList<>();
        
        // Sempre incluir primeiro e último ponto
        simplified.add(points.get(0));
        
        // Selecionar pontos distribuídos uniformemente
        int step = points.size() / (maxPoints - 1);
        for (int i = step; i < points.size() - step; i += step) {
            simplified.add(points.get(i));
        }
        
        // Sempre incluir último ponto
        if (points.size() > 1) {
            simplified.add(points.get(points.size() - 1));
        }
        
        return simplified;
    }
    
    private static int getMinPixelsForSeverity(String severity) {
        switch (severity) {
            case "severa": return 25;    // Era 15, agora 25
            case "forte": return 35;     // Era 20, agora 35
            case "moderada": return 50;  // Era 30, agora 50
            default: return 25;
        }
    }
    
    private static void generateOutputs(List<HSVPolygon> polygons, String imagePath) {
        try {
            BufferedImage originalImage = ImageIO.read(new File(imagePath));
            
            // Gerar imagens por severidade
            String[] severities = {"moderada", "forte", "severa"};
            Color[] colors = {Color.GREEN, new Color(153, 0, 255), new Color(255, 102, 0)}; // Verde, Roxo, Laranja
            
            for (int i = 0; i < severities.length; i++) {
                generateSeverityImage(originalImage, polygons, severities[i], colors[i]);
            }
            
            // Gerar JSON
            generateJSON(polygons, originalImage.getWidth(), originalImage.getHeight());
            
            log.info(String.valueOf("📊 Polígonos encontrados: " + polygons.size()));
            
        } catch (IOException e) {
            log.warn(String.valueOf("Erro ao gerar outputs: " + e.getMessage()));
        }
    }
    
    private static void generateSeverityImage(BufferedImage original, List<HSVPolygon> polygons, String severity, Color color) {
        try {
            BufferedImage output = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = output.createGraphics();
            g2d.drawImage(original, 0, 0, null);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
            g2d.setStroke(new BasicStroke(2));
            
            for (HSVPolygon polygon : polygons) {
                if (polygon.severity.equals(severity)) {
                    // Desenhar polígono real
                    int[] xPoints = new int[polygon.contourPoints.size()];
                    int[] yPoints = new int[polygon.contourPoints.size()];
                    
                    for (int i = 0; i < polygon.contourPoints.size(); i++) {
                        xPoints[i] = polygon.contourPoints.get(i).x;
                        yPoints[i] = polygon.contourPoints.get(i).y;
                    }
                    
                    g2d.fillPolygon(xPoints, yPoints, polygon.contourPoints.size());
                    g2d.setColor(color);
                    g2d.drawPolygon(xPoints, yPoints, polygon.contourPoints.size());
                    g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
                }
            }
            
            g2d.dispose();
            ImageIO.write(output, "PNG", new File("data/hsv_optimized_" + severity + ".png"));
            
        } catch (IOException e) {
            log.warn(String.valueOf("Erro ao gerar imagem " + severity + ": " + e.getMessage()));
        }
    }
    
    private static void generateJSON(List<HSVPolygon> polygons, int imageWidth, int imageHeight) {
        try (FileWriter writer = new FileWriter("data/hsv_optimized.json")) {
            writer.write("{\n");
            writer.write("  \"type\": \"FeatureCollection\",\n");
            writer.write("  \"features\": [\n");
            
            // Bounds geográficos da imagem (MESMOS DO CANAL 16)
            double minLon = -118.52, maxLon = -24.09;
            double minLat = -53.2, maxLat = 38.8;
            
            for (int i = 0; i < polygons.size(); i++) {
                HSVPolygon polygon = polygons.get(i);
                writer.write("    {\n");
                writer.write("      \"type\": \"Feature\",\n");
                writer.write("      \"properties\": {\n");
                writer.write("        \"severity\": \"" + polygon.severity + "\",\n");
                writer.write("        \"pixelCount\": " + polygon.pixelCount + ",\n");
                writer.write("        \"area\": " + polygon.area + ",\n");
                writer.write("        \"points\": " + polygon.contourPoints.size() + ",\n");
                writer.write("        \"method\": \"HSV-Optimized\"\n");
                writer.write("      },\n");
                writer.write("      \"geometry\": {\n");
                writer.write("        \"type\": \"Polygon\",\n");
                writer.write("        \"coordinates\": [[\n");
                
                for (int j = 0; j < polygon.contourPoints.size(); j++) {
                    Point p = polygon.contourPoints.get(j);
                    double lon = minLon + ((double)p.x / imageWidth) * (maxLon - minLon);
                    double lat = minLat + ((imageHeight - (double)p.y) / imageHeight) * (maxLat - minLat);
                    
                    writer.write("          [" + lon + ", " + lat + "]");
                    if (j < polygon.contourPoints.size() - 1) writer.write(",");
                    writer.write("\n");
                }
                
                writer.write("        ]]\n");
                writer.write("      }\n");
                writer.write("    }");
                if (i < polygons.size() - 1) writer.write(",");
                writer.write("\n");
            }
            
            writer.write("  ],\n");
            writer.write("  \"total_polygons\": " + polygons.size() + ",\n");
            writer.write("  \"timestamp\": \"" + java.time.Instant.now() + "\"\n");
            writer.write("}\n");
            
        } catch (IOException e) {
            log.warn(String.valueOf("Erro ao gerar JSON: " + e.getMessage()));
        }
    }
}
