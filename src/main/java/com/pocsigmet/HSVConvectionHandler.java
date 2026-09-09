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

public class HSVConvectionHandler {
    private static final Logger log = LoggerFactory.getLogger(HSVConvectionHandler.class);
    
    // Fallback usando Java AWT quando OpenCV não estiver disponível
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
    
    public static List<HSVRegion> detectConvectionHSV(String imagePath) {
        try {
            BufferedImage image = ImageIO.read(new File(imagePath));
            return analyzeImageHSV(image);
        } catch (IOException e) {
            log.warn(String.valueOf("Erro ao carregar imagem HSV: " + e.getMessage()));
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
                    String severity = classifyPixelHSV(pixel);
                    
                    if (severity != null) {
                        HSVRegion region = floodFillHSV(image, visited, x, y, severity);
                        if (region.pixelCount >= getMinPixelsForSeverity(severity)) {
                            regions.add(region);
                        }
                    }
                }
            }
        }
        
        log.info("HSV Fallback: Detectadas " + regions.size() + " regiões");
        return regions;
    }
    
    private static String classifyPixelHSV(Color color) {
        float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsv[0] * 360;        // 0-360
        float saturation = hsv[1] * 100; // 0-100
        float brightness = hsv[2] * 100; // 0-100
        
        // Filtrar pixels muito escuros ou sem saturação
        // Exceção para lilas/roxo (270-330): permite valores mais baixos
        if (hue >= 270 && hue <= 330) {
            if (brightness < 15 || saturation < 20) {
                return null;
            }
        } else {
            if (brightness < 20 || saturation < 30) {
                return null;
            }
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
    
    private static HSVRegion floodFillHSV(BufferedImage image, boolean[][] visited, int startX, int startY, String severity) {
        Stack<Point> stack = new Stack<>();
        stack.push(new Point(startX, startY));
        
        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;
        int pixelCount = 0;
        
        while (!stack.isEmpty()) {
            Point p = stack.pop();
            int x = p.x, y = p.y;
            
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
            
            // Adicionar vizinhos
            stack.push(new Point(x+1, y));
            stack.push(new Point(x-1, y));
            stack.push(new Point(x, y+1));
            stack.push(new Point(x, y-1));
        }
        
        return new HSVRegion(minX, minY, maxX - minX, maxY - minY, severity, pixelCount);
    }
    
    private static int getMinPixelsForSeverity(String severity) {
        switch (severity) {
            case "SEVERA": return 5;     // Era 10, agora 5
            case "FORTE": return 8;      // Era 15, agora 8  
            case "MODERADA": return 12;  // Era 25, agora 12
            default: return 5;
        }
    }
    
    public static BufferedImage generateHSVRaster(String imagePath, List<HSVRegion> regions, String severityFilter) {
        try {
            BufferedImage image = ImageIO.read(new File(imagePath));
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            for (HSVRegion region : regions) {
                // Filtrar por severidade se especificado
                if (severityFilter != null && !region.severity.equals(severityFilter)) {
                    continue;
                }
                
                // Cor baseada na severidade
                Color color = getColorForSeverity(region.severity);
                
                // Desenhar região semi-transparente - SEM LABELS
                g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
                g2d.fillRect(region.x, region.y, region.width, region.height);
                
                // Borda mais sutil
                g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 200));
                g2d.setStroke(new BasicStroke(1));
                g2d.drawRect(region.x, region.y, region.width, region.height);
            }
            
            g2d.dispose();
            return image;
            
        } catch (IOException e) {
            log.warn(String.valueOf("Erro ao gerar raster HSV: " + e.getMessage()));
            return null;
        }
    }
    
    private static Color getColorForSeverity(String severity) {
        switch (severity) {
            case "SEVERA": return Color.RED;
            case "FORTE": return new Color(128, 0, 128); // Roxo
            case "MODERADA": return Color.GREEN;
            default: return Color.WHITE;
        }
    }
    
    // Classe para representar polígonos SIGMET agrupados
    public static class SigmetPolygon {
        public java.util.List<Point> boundingBox;
        public String severity;
        public int totalPixels;
        public java.util.List<HSVRegion> mergedRegions;
        
        public SigmetPolygon(String severity) {
            this.severity = severity;
            this.boundingBox = new ArrayList<>();
            this.mergedRegions = new ArrayList<>();
            this.totalPixels = 0;
        }
    }
    
    // Algoritmo de clustering para agrupar regiões próximas
    private static List<SigmetPolygon> clusterRegions(List<HSVRegion> regions, int maxDistance) {
        Map<String, List<HSVRegion>> regionsBySeverity = new HashMap<>();
        
        // Agrupar por severidade
        for (HSVRegion region : regions) {
            regionsBySeverity.computeIfAbsent(region.severity, k -> new ArrayList<>()).add(region);
        }
        
        List<SigmetPolygon> sigmetPolygons = new ArrayList<>();
        
        // Processar cada severidade separadamente
        for (Map.Entry<String, List<HSVRegion>> entry : regionsBySeverity.entrySet()) {
            String severity = entry.getKey();
            List<HSVRegion> severityRegions = entry.getValue();
            
            boolean[] clustered = new boolean[severityRegions.size()];
            
            for (int i = 0; i < severityRegions.size(); i++) {
                if (clustered[i]) continue;
                
                SigmetPolygon polygon = new SigmetPolygon(severity);
                List<HSVRegion> cluster = new ArrayList<>();
                
                // Flood fill para encontrar regiões conectadas
                Stack<Integer> stack = new Stack<>();
                stack.push(i);
                
                while (!stack.isEmpty()) {
                    int idx = stack.pop();
                    if (clustered[idx]) continue;
                    
                    clustered[idx] = true;
                    HSVRegion current = severityRegions.get(idx);
                    cluster.add(current);
                    polygon.totalPixels += current.pixelCount;
                    
                    // Procurar vizinhos próximos
                    for (int j = 0; j < severityRegions.size(); j++) {
                        if (clustered[j]) continue;
                        
                        HSVRegion neighbor = severityRegions.get(j);
                        if (areRegionsClose(current, neighbor, maxDistance)) {
                            stack.push(j);
                        }
                    }
                }
                
                // Criar bounding box do cluster
                if (!cluster.isEmpty()) {
                    polygon.mergedRegions = cluster;
                    polygon.boundingBox = createSimpleGrid(cluster);
                    sigmetPolygons.add(polygon);
                }
            }
        }
        
        log.info("Clustering: " + regions.size() + " regiões -> " + sigmetPolygons.size() + " polígonos SIGMET");
        return sigmetPolygons;
    }
    
    // Verifica se duas regiões estão próximas o suficiente para serem agrupadas
    private static boolean areRegionsClose(HSVRegion r1, HSVRegion r2, int maxDistance) {
        int centerX1 = r1.x + r1.width / 2;
        int centerY1 = r1.y + r1.height / 2;
        int centerX2 = r2.x + r2.width / 2;
        int centerY2 = r2.y + r2.height / 2;
        
        double distance = Math.sqrt(Math.pow(centerX2 - centerX1, 2) + Math.pow(centerY2 - centerY1, 2));
        return distance <= maxDistance;
    }
    
    // Cria quadrados pequenos por região individual
    private static List<Point> createSimpleGrid(List<HSVRegion> regions) {
        if (regions.isEmpty()) return new ArrayList<>();
        
        // Usar primeira região como base (quadrado pequeno)
        HSVRegion first = regions.get(0);
        int size = Math.max(first.width, first.height) + 10; // Pequeno buffer
        
        List<Point> corners = new ArrayList<>();
        corners.add(new Point(first.x - 5, first.y - 5));
        corners.add(new Point(first.x + size, first.y - 5));
        corners.add(new Point(first.x + size, first.y + size));
        corners.add(new Point(first.x - 5, first.y + size));
        
        return corners;
    }

    // Cria polígono real seguindo o contorno das regiões
    private static List<Point> createConvexHull(List<HSVRegion> regions) {
        List<Point> points = new ArrayList<>();
        
        // Coletar todos os pontos das regiões
        for (HSVRegion region : regions) {
            points.add(new Point(region.x, region.y));
        }
        
        if (points.size() < 3) {
            return createBoundingBox(regions);
        }
        
        // Algoritmo de Graham Scan para convex hull
        return grahamScan(points);
    }
    
    // Implementação do algoritmo Graham Scan
    private static List<Point> grahamScan(List<Point> points) {
        if (points.size() < 3) return points;
        
        // Encontrar ponto mais baixo
        Point pivot = points.stream()
            .min((a, b) -> a.y != b.y ? Integer.compare(a.y, b.y) : Integer.compare(a.x, b.x))
            .orElse(points.get(0));
        
        // Ordenar pontos por ângulo polar
        points.sort((a, b) -> {
            if (a.equals(pivot)) return -1;
            if (b.equals(pivot)) return 1;
            
            long cross = crossProduct(pivot, a, b);
            if (cross == 0) {
                return Long.compare(distanceSquared(pivot, a), distanceSquared(pivot, b));
            }
            return cross > 0 ? -1 : 1;
        });
        
        // Construir convex hull
        List<Point> hull = new ArrayList<>();
        for (Point p : points) {
            while (hull.size() >= 2 && 
                   crossProduct(hull.get(hull.size()-2), hull.get(hull.size()-1), p) <= 0) {
                hull.remove(hull.size()-1);
            }
            hull.add(p);
        }
        
        return hull;
    }
    
    private static long crossProduct(Point O, Point A, Point B) {
        return (long)(A.x - O.x) * (B.y - O.y) - (long)(A.y - O.y) * (B.x - O.x);
    }
    
    private static long distanceSquared(Point a, Point b) {
        long dx = a.x - b.x;
        long dy = a.y - b.y;
        return dx * dx + dy * dy;
    }

    // Cria bounding box (retângulo envolvente) para um cluster de regiões
    private static List<Point> createBoundingBox(List<HSVRegion> regions) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        
        for (HSVRegion region : regions) {
            minX = Math.min(minX, region.x);
            maxX = Math.max(maxX, region.x + region.width);
            minY = Math.min(minY, region.y);
            maxY = Math.max(maxY, region.y + region.height);
        }
        
        // Retornar 4 cantos do retângulo (sentido horário)
        List<Point> corners = new ArrayList<>();
        corners.add(new Point(minX, minY)); // Superior esquerdo
        corners.add(new Point(maxX, minY)); // Superior direito
        corners.add(new Point(maxX, maxY)); // Inferior direito
        corners.add(new Point(minX, maxY)); // Inferior esquerdo
        
        return corners;
    }

    public static String regionsToGeoJSON(List<HSVRegion> regions, int imageWidth, int imageHeight) {
        StringBuilder json = new StringBuilder();
        json.append("{\"type\":\"FeatureCollection\",\"features\":[");
        
        // Bounds geográficos da imagem (MESMOS DO CANAL 16)
        double minLon = -119.8125;
        double maxLon = -23.4375;
        double minLat = -53.75;
        double maxLat = 40.25;
        
        // Filtrar só regiões SEVERA antes do clustering
        List<HSVRegion> severaRegions = regions.stream()
            .filter(r -> "SEVERA".equals(r.severity))
            .collect(java.util.stream.Collectors.toList());
        
        // Fazer clustering só das regiões SEVERA
        List<SigmetPolygon> sigmetPolygons = clusterRegions(severaRegions, 50);
        
        int featureCount = 0;
        
        // 1. ADICIONAR POLÍGONOS SIGMET (agrupados)
        for (SigmetPolygon polygon : sigmetPolygons) {
            if (featureCount > 0) json.append(",");
            
            json.append("{");
            json.append("\"type\":\"Feature\",");
            json.append("\"properties\":{");
            json.append("\"severity\":\"").append(polygon.severity).append("\",");
            json.append("\"pixels\":").append(polygon.totalPixels).append(",");
            json.append("\"regions\":").append(polygon.mergedRegions.size()).append(",");
            json.append("\"method\":\"HSV-SIGMET\",");
            json.append("\"sigmet\":true");
            json.append("},");
            json.append("\"geometry\":{");
            json.append("\"type\":\"Polygon\",");
            json.append("\"coordinates\":[[");
            
            // Converter cantos do polígono para coordenadas geográficas
            for (int i = 0; i < polygon.boundingBox.size(); i++) {
                Point corner = polygon.boundingBox.get(i);
                double lon = minLon + ((double)corner.x / imageWidth) * (maxLon - minLon);
                double lat = minLat + ((imageHeight - (double)corner.y) / imageHeight) * (maxLat - minLat);
                
                if (i > 0) json.append(",");
                json.append("[").append(lon).append(",").append(lat).append("]");
            }
            
            // Fechar polígono (repetir primeiro ponto)
            Point firstCorner = polygon.boundingBox.get(0);
            double firstLon = minLon + ((double)firstCorner.x / imageWidth) * (maxLon - minLon);
            double firstLat = minLat + ((imageHeight - (double)firstCorner.y) / imageHeight) * (maxLat - minLat);
            json.append(",[").append(firstLon).append(",").append(firstLat).append("]");
            
            json.append("]]");
            json.append("}");
            json.append("}");
            featureCount++;
        }
        
        // 2. ADICIONAR PONTOS ORIGINAIS (para visualização detalhada)
        for (int i = 0; i < regions.size(); i++) {
            HSVRegion region = regions.get(i);
            
            if (featureCount > 0) json.append(",");
            
            // Centro da região
            double centerX = region.x + region.width / 2.0;
            double centerY = region.y + region.height / 2.0;
            
            // Converter para coordenadas geográficas
            double lon = minLon + (centerX / imageWidth) * (maxLon - minLon);
            double lat = minLat + ((imageHeight - centerY) / imageHeight) * (maxLat - minLat);
            
            json.append("{");
            json.append("\"type\":\"Feature\",");
            json.append("\"properties\":{");
            json.append("\"severity\":\"").append(region.severity).append("\",");
            json.append("\"pixels\":").append(region.pixelCount).append(",");
            json.append("\"method\":\"HSV-Point\",");
            json.append("\"sigmet\":false");
            json.append("},");
            json.append("\"geometry\":{");
            json.append("\"type\":\"Point\",");
            json.append("\"coordinates\":[").append(lon).append(",").append(lat).append("]");
            json.append("}");
            json.append("}");
            featureCount++;
        }
        
        json.append("],");
        json.append("\"total_features\":").append(featureCount).append(",");
        json.append("\"sigmet_polygons\":").append(sigmetPolygons.size()).append(",");
        json.append("\"original_regions\":").append(regions.size()).append(",");
        json.append("\"timestamp\":\"").append(java.time.Instant.now()).append("\"");
        json.append("}");
        
        log.info("GeoJSON gerado: " + sigmetPolygons.size() + " polígonos SIGMET + " + regions.size() + " pontos originais");
        return json.toString();
    }
    
    public static void main(String[] args) {
        try {
            String imagePath = getMostRecentCanal16Image();
            log.warn(String.valueOf("DEBUG HSV Handler: Usando imagem: " + imagePath));
            
            List<HSVRegion> regions = detectConvectionHSV(imagePath);
            
            // Gerar 3 rasters separados
            BufferedImage rasterSevera = generateHSVRaster(imagePath, regions, "SEVERA");
            BufferedImage rasterForte = generateHSVRaster(imagePath, regions, "FORTE");
            BufferedImage rasterModerada = generateHSVRaster(imagePath, regions, "MODERADA");
            
            if (rasterSevera != null) {
                ImageIO.write(rasterSevera, "PNG", new File("data/convection_hsv_severa.png"));
                log.info("Raster HSV SEVERA gerado: data/convection_hsv_severa.png");
            }
            
            if (rasterForte != null) {
                ImageIO.write(rasterForte, "PNG", new File("data/convection_hsv_forte.png"));
                log.info("Raster HSV FORTE gerado: data/convection_hsv_forte.png");
            }
            
            if (rasterModerada != null) {
                ImageIO.write(rasterModerada, "PNG", new File("data/convection_hsv_moderada.png"));
                log.info("Raster HSV MODERADA gerado: data/convection_hsv_moderada.png");
            }
            
            // Gerar GeoJSON com polígonos SIGMET
            BufferedImage image = ImageIO.read(new File(imagePath));
            String geoJSON = regionsToGeoJSON(regions, image.getWidth(), image.getHeight());
            
            // Salvar polígonos SIGMET
            try (FileWriter writer = new FileWriter("data/convection_hsv_polygon.json")) {
                writer.write(geoJSON);
                log.info("Polígonos SIGMET salvos em: data/convection_hsv_polygon.json");
            }
            
            // log.info(String.valueOf(geoJSON)); // Removido para não poluir logs
            
        } catch (Exception e) {
            log.warn(String.valueOf("Erro: " + e.getMessage()));
            e.printStackTrace();
        }
    }
    
    private static String getMostRecentCanal16Image() {
        File dataDir = new File("data");
        File[] files = dataDir.listFiles((dir, name) -> name.startsWith("canal16_202") && name.endsWith(".jpg"));
        if (files != null && files.length > 0) {
            Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
            log.info(String.valueOf("DEBUG HSV Handler: Usando imagem: " + files[0].getAbsolutePath()));
            return files[0].getAbsolutePath();
        }
        return "data/canal16_latest.jpg";
    }
}
