package com.pocsigmet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class HSVPolygonGenerator {
    private static final Logger log = LoggerFactory.getLogger(HSVPolygonGenerator.class);
    
    static {
        nu.pattern.OpenCV.loadLocally();
    }
    
    static class HSVRegion {
        int x, y, width, height;
        String severity;
        int pixelCount;
        
        HSVRegion(int x, int y, int width, int height, String severity, int pixelCount) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.severity = severity; this.pixelCount = pixelCount;
        }
        
        boolean isAdjacent(HSVRegion other, int threshold) {
            int dx = Math.abs(this.x - other.x);
            int dy = Math.abs(this.y - other.y);
            return dx <= threshold && dy <= threshold;
        }
    }
    
    static class PolygonGroup {
        List<HSVRegion> regions = new ArrayList<>();
        String severity;
        
        void addRegion(HSVRegion region) {
            regions.add(region);
            if (severity == null) severity = region.severity;
        }
        
        // Gerar polígono CONVEXO REAL ao redor das regiões
        double[][] getBoundingPolygon() {
            if (regions.isEmpty()) return new double[0][0];
            
            // Coletar TODOS os pontos dos cantos dos quadradinhos
            List<double[]> allPoints = new ArrayList<>();
            for (HSVRegion region : regions) {
                allPoints.add(new double[]{region.x, region.y});
                allPoints.add(new double[]{region.x + region.width, region.y});
                allPoints.add(new double[]{region.x, region.y + region.height});
                allPoints.add(new double[]{region.x + region.width, region.y + region.height});
            }
            
            // Calcular HULL CONVEXO real (algoritmo Graham)
            return convexHull(allPoints);
        }
        
        // Algoritmo Hull Convexo simples
        private double[][] convexHull(List<double[]> points) {
            if (points.size() < 3) {
                // Fallback para retângulo se poucos pontos
                double minX = points.stream().mapToDouble(p -> p[0]).min().orElse(0);
                double maxX = points.stream().mapToDouble(p -> p[0]).max().orElse(0);
                double minY = points.stream().mapToDouble(p -> p[1]).min().orElse(0);
                double maxY = points.stream().mapToDouble(p -> p[1]).max().orElse(0);
                
                return new double[][] {
                    {minX, minY}, {maxX, minY}, {maxX, maxY}, {minX, maxY}, {minX, minY}
                };
            }
            
            // Ordenar pontos por X, depois Y
            points.sort((a, b) -> {
                int cmp = Double.compare(a[0], b[0]);
                return cmp != 0 ? cmp : Double.compare(a[1], b[1]);
            });
            
            // Hull inferior
            List<double[]> lower = new ArrayList<>();
            for (double[] p : points) {
                while (lower.size() >= 2 && cross(lower.get(lower.size()-2), lower.get(lower.size()-1), p) <= 0) {
                    lower.remove(lower.size()-1);
                }
                lower.add(p);
            }
            
            // Hull superior
            List<double[]> upper = new ArrayList<>();
            for (int i = points.size()-1; i >= 0; i--) {
                double[] p = points.get(i);
                while (upper.size() >= 2 && cross(upper.get(upper.size()-2), upper.get(upper.size()-1), p) <= 0) {
                    upper.remove(upper.size()-1);
                }
                upper.add(p);
            }
            
            // Remover último ponto duplicado
            lower.remove(lower.size()-1);
            upper.remove(upper.size()-1);
            
            // Combinar
            lower.addAll(upper);
            lower.add(lower.get(0)); // Fechar polígono
            
            return lower.toArray(new double[0][]);
        }
        
        private double cross(double[] O, double[] A, double[] B) {
            return (A[0] - O[0]) * (B[1] - O[1]) - (A[1] - O[1]) * (B[0] - O[0]);
        }
        
        int getTotalPixels() {
            return regions.stream().mapToInt(r -> r.pixelCount).sum();
        }
    }
    
    public static void main(String[] args) {
        try {
            // 1. Usar mesma lógica do HSVNoOverlap para pegar imagem
            String imagePath = getMostRecentCanal16Image();
            log.warn(String.valueOf("DEBUG HSV Polygon: Usando imagem: " + imagePath));
            
            BufferedImage image = ImageIO.read(new File(imagePath));
            
            // 2. Detectar regiões SEVERAS (copiando algoritmo HSVNoOverlap)
            List<HSVRegion> severeRegions = detectSevereRegions(imagePath);
            log.warn("HSV Polygon: Detectadas " + severeRegions.size() + " regiões SEVERAS");
            
            // 3. Agrupar regiões próximas
            List<PolygonGroup> polygonGroups = groupAdjacentRegions(severeRegions);
            log.warn("HSV Polygon: Agrupadas em " + polygonGroups.size() + " polígonos");
            
            // 4. Gerar GeoJSON
            String geoJSON = polygonsToGeoJSON(polygonGroups, image.getWidth(), image.getHeight());
            log.info(String.valueOf(geoJSON));
            
        } catch (Exception e) {
            e.printStackTrace();
            log.info("{\"error\":\"" + e.getMessage() + "\",\"features\":[],\"total\":0}");
        }
    }
    
    private static String getMostRecentCanal16Image() {
        // Copiar lógica do HSVNoOverlap
        File dataDir = new File("data");
        File[] files = dataDir.listFiles((dir, name) -> 
            name.startsWith("canal16_") && name.endsWith(".jpg") && !name.contains("latest"));
        
        if (files == null || files.length == 0) {
            return "data/canal16_latest.jpg";
        }
        
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files[0].getAbsolutePath();
    }
    
    private static List<HSVRegion> detectSevereRegions(String imagePath) {
        List<HSVRegion> severeRegions = new ArrayList<>();
        
        try {
            // Usar OpenCV igual ao HSVNoOverlap
            Mat image = Imgcodecs.imread(imagePath);
            Mat hsv = new Mat();
            Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);
            
            // Definir ranges HSV para SEVERA (copiar do HSVConvectionHandler)
            Scalar lowerSevere = new Scalar(0, 100, 100);   // Vermelho/Laranja
            Scalar upperSevere = new Scalar(25, 255, 255);
            
            Mat maskSevere = new Mat();
            Core.inRange(hsv, lowerSevere, upperSevere, maskSevere);
            
            // Detectar regiões em janelas pequenas (igual HSVNoOverlap)
            int windowSize = 10;
            for (int y = 0; y < image.rows() - windowSize; y += windowSize) {
                for (int x = 0; x < image.cols() - windowSize; x += windowSize) {
                    Rect window = new Rect(x, y, windowSize, windowSize);
                    Mat windowMask = new Mat(maskSevere, window);
                    
                    int pixelCount = Core.countNonZero(windowMask);
                    if (pixelCount > windowSize * windowSize * 0.3) { // 30% threshold
                        severeRegions.add(new HSVRegion(x, y, windowSize, windowSize, "SEVERA", pixelCount));
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn(String.valueOf("Erro na detecção HSV: " + e.getMessage()));
        }
        
        return severeRegions;
    }
    
    private static List<PolygonGroup> groupAdjacentRegions(List<HSVRegion> regions) {
        List<PolygonGroup> groups = new ArrayList<>();
        boolean[] used = new boolean[regions.size()];
        int adjacencyThreshold = 15; // AJUSTADO: pixels
        
        for (int i = 0; i < regions.size(); i++) {
            if (used[i]) continue;
            
            PolygonGroup group = new PolygonGroup();
            Queue<Integer> queue = new LinkedList<>();
            queue.add(i);
            used[i] = true;
            
            // BFS para agrupar regiões adjacentes
            while (!queue.isEmpty()) {
                int current = queue.poll();
                group.addRegion(regions.get(current));
                
                // Buscar regiões adjacentes
                for (int j = 0; j < regions.size(); j++) {
                    if (!used[j] && regions.get(current).isAdjacent(regions.get(j), adjacencyThreshold)) {
                        used[j] = true;
                        queue.add(j);
                    }
                }
            }
            
            // FILTRO BALANCEADO: pelo menos 4 regiões
            if (group.regions.size() >= 4) {
                groups.add(group);
            }
        }
        
        return groups;
    }
    
    private static String polygonsToGeoJSON(List<PolygonGroup> groups, int imageWidth, int imageHeight) {
        StringBuilder json = new StringBuilder();
        json.append("{\"type\":\"FeatureCollection\",\"features\":[");
        
        for (int i = 0; i < groups.size(); i++) {
            PolygonGroup group = groups.get(i);
            double[][] polygon = group.getBoundingPolygon();
            
            if (i > 0) json.append(",");
            json.append("{\"type\":\"Feature\",");
            json.append("\"properties\":{");
            json.append("\"severity\":\"").append(group.severity).append("\",");
            json.append("\"regionCount\":").append(group.regions.size()).append(",");
            json.append("\"totalPixels\":").append(group.getTotalPixels());
            json.append("},");
            json.append("\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[");
            
            // Converter coordenadas pixel para geográficas
            for (int j = 0; j < polygon.length; j++) {
                double[] coord = pixelToGeo(polygon[j][0], polygon[j][1], imageWidth, imageHeight);
                if (j > 0) json.append(",");
                json.append("[").append(coord[0]).append(",").append(coord[1]).append("]");
            }
            
            json.append("]]}}");
        }
        
        json.append("],\"total\":").append(groups.size()).append("}");
        return json.toString();
    }
    
    private static double[] pixelToGeo(double pixelX, double pixelY, int imageWidth, int imageHeight) {
        // Usar mesma conversão do HSVNoOverlap
        double minLon = -118.52;
        double maxLon = -24.09;
        double minLat = -53.2;
        double maxLat = 38.8;
        
        double lon = minLon + (pixelX / imageWidth) * (maxLon - minLon);
        double lat = maxLat - (pixelY / imageHeight) * (maxLat - minLat);
        
        return new double[]{lon, lat};
    }
}
