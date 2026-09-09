package com.pocsigmet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SevereConvectionSIGMET {
    private static final Logger log = LoggerFactory.getLogger(SevereConvectionSIGMET.class);
    static { nu.pattern.OpenCV.loadLocally(); }

    public String processImage2() throws IOException {
        // Limites geográficos da imagem (ajuste conforme necessário)
        double minLat = -60.0, maxLat = 15.0;
        double minLon = -90.0, maxLon = -30.0;

        // Carregar imagem mais recente do Canal 16
        File dataDir = new File("data");
        File[] images = dataDir.listFiles((dir, name) -> 
            name.startsWith("canal16_") && name.endsWith(".jpg") && new File(dir, name).length() > 1000);
        
        if (images == null || images.length == 0) {
            throw new IOException("Nenhuma imagem Canal 16 encontrada");
        }
        
        // Usar a mais recente
        File latestImage = images[0];
        for (File img : images) {
            if (img.lastModified() > latestImage.lastModified()) {
                latestImage = img;
            }
        }
        
        Mat image = Imgcodecs.imread(latestImage.getPath());
        int imgWidth = image.width();
        int imgHeight = image.height();

        // Converter para HSV
        Mat hsv = new Mat();
        Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);

        // Faixas HSV para vermelho e roxo (convecção severa)
        Scalar lowerRed1 = new Scalar(0, 80, 80);
        Scalar upperRed1 = new Scalar(10, 255, 255);
        Scalar lowerRed2 = new Scalar(160, 80, 80);
        Scalar upperRed2 = new Scalar(180, 255, 255);
        Scalar lowerPurple = new Scalar(125, 50, 50);
        Scalar upperPurple = new Scalar(155, 255, 255);

        // Criar máscara
        Mat mask = new Mat();
        Core.inRange(hsv, lowerRed1, upperRed1, mask);
        Mat temp = new Mat();
        Core.inRange(hsv, lowerRed2, upperRed2, temp);
        Core.bitwise_or(mask, temp, mask);
        Core.inRange(hsv, lowerPurple, upperPurple, temp);
        Core.bitwise_or(mask, temp, mask);

        // Encontrar contornos
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Criar GeoJSON
        StringBuilder geojson = new StringBuilder();
        geojson.append("{\"type\": \"FeatureCollection\", \"features\": [");

        int count = 0;
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > 20) { // polígonos muito pequenos incluídos
                MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                MatOfPoint2f approxCurve = new MatOfPoint2f();
                Imgproc.approxPolyDP(contour2f, approxCurve, 0.001 * Imgproc.arcLength(contour2f, true), true);

                // Construir coordenadas e mensagem SIGMET
                StringBuilder coordsArray = new StringBuilder();
                StringBuilder coordText = new StringBuilder();
                Point[] points = approxCurve.toArray();
                coordsArray.append("[");
                for (int i = 0; i < points.length; i++) {
                    double lon = minLon + (points[i].x / imgWidth) * (maxLon - minLon);
                    double lat = maxLat - (points[i].y / imgHeight) * (maxLat - minLat);
                    coordsArray.append("[").append(lon).append(", ").append(lat).append("]");
                    coordText.append("(").append(String.format("%.2f", lat)).append("°N, ")
                             .append(String.format("%.2f", lon)).append("°W)");
                    if (i < points.length - 1) {
                        coordsArray.append(", ");
                        coordText.append(", ");
                    }
                }
                coordsArray.append("]");

                String sigmetMsg = String.format(
                    "SIGMET %d - Convecção severa. Área delimitada pelas coordenadas: %s. Altitude: FL390 (~12000 m).",
                    count, coordText.toString()
                );

                geojson.append("{\"type\": \"Feature\", \"geometry\": {\"type\": \"Polygon\", \"coordinates\": [")
                       .append(coordsArray)
                       .append("]}, \"properties\": {\"id\": ").append(count)
                       .append(", \"altitude_m\": 12000, \"risk\": \"ALTO\", \"sigmet\": \"")
                       .append(sigmetMsg.replace("\"", "\\\""))
                       .append("\"}},");
                count++;
            }
        }

        if (geojson.charAt(geojson.length() - 1) == ',') {
            geojson.deleteCharAt(geojson.length() - 1);
        }
        geojson.append("]}");

        // Salvar arquivo GeoJSON
        try (FileWriter writer = new FileWriter("data/severe_convection_with_sigmet.geojson")) {
            writer.write(geojson.toString());
        }

        log.info("GeoJSON com SIGMET gerado: severe_convection_with_sigmet.geojson");
        return geojson.toString();
    }	
	
    public void processImage3() throws IOException {
        // Carregar imagem
		
		 File dataDir = new File("data");
        File[] images = dataDir.listFiles((dir, name) -> 
            name.startsWith("canal16_") && name.endsWith(".jpg") && new File(dir, name).length() > 1000);
        
        if (images == null || images.length == 0) {
            throw new IOException("Nenhuma imagem Canal 16 encontrada");
        }
        
        // Usar a mais recente
        File latestImage = images[0];
        for (File img : images) {
            if (img.lastModified() > latestImage.lastModified()) {
                latestImage = img;
            }
        }
        
		log.info(String.valueOf(" image path --- " + latestImage.getPath()));
        Mat image = Imgcodecs.imread(latestImage.getPath());		
		
		// end of image load
		
		
        //Mat image = Imgcodecs.imread("canal16_latest.jpg");
        int imgWidth = image.width();
        int imgHeight = image.height();

        // Converter para HSV
        Mat hsv = new Mat();
        Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);

        // Faixas HSV para vermelho e roxo (convecção severa)
        Scalar lowerRed1 = new Scalar(0, 80, 80);
        Scalar upperRed1 = new Scalar(10, 255, 255);
        Scalar lowerRed2 = new Scalar(160, 80, 80);
        Scalar upperRed2 = new Scalar(180, 255, 255);
        Scalar lowerPurple = new Scalar(125, 50, 50);
        Scalar upperPurple = new Scalar(155, 255, 255);

        // Criar máscara
        Mat mask = new Mat();
        Core.inRange(hsv, lowerRed1, upperRed1, mask);
        Mat temp = new Mat();
        Core.inRange(hsv, lowerRed2, upperRed2, temp);
        Core.bitwise_or(mask, temp, mask);
        Core.inRange(hsv, lowerPurple, upperPurple, temp);
        Core.bitwise_or(mask, temp, mask);

        // Encontrar contornos
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Criar GeoJSON com coordenadas em pixels
        StringBuilder geojson = new StringBuilder();
        geojson.append("{\"type\": \"FeatureCollection\", \"features\": [");

        int count = 0;
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > 20) { // polígonos pequenos incluídos
                MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                MatOfPoint2f approxCurve = new MatOfPoint2f();
                Imgproc.approxPolyDP(contour2f, approxCurve, 0.001 * Imgproc.arcLength(contour2f, true), true);

                // Construir coordenadas em pixels
                StringBuilder coordsArray = new StringBuilder();
                coordsArray.append("[");
                Point[] points = approxCurve.toArray();
                for (int i = 0; i < points.length; i++) {
                    coordsArray.append("[").append((int) points[i].x).append(", ").append((int) points[i].y).append("]");
                    if (i < points.length - 1) coordsArray.append(", ");
                }
                coordsArray.append("]");

                // Mensagem SIGMET
                String sigmetMsg = String.format(
                    "SIGMET %d - Convecção severa detectada. Coordenadas em pixels: %s. Altitude: FL390 (~12000 m).",
                    count, coordsArray.toString()
                );

                geojson.append("{\"type\": \"Feature\", \"geometry\": {\"type\": \"Polygon\", \"coordinates\": [")
                       .append(coordsArray)
                       .append("]}, \"properties\": {\"id\": ").append(count)
                       .append(", \"altitude_m\": 12000, \"risk\": \"ALTO\", \"sigmet\": \"")
                       .append(sigmetMsg.replace("\"", "\\\""))
                       .append("\"}},");
                count++;
            }
        }

        if (geojson.charAt(geojson.length() - 1) == ',') {
            geojson.deleteCharAt(geojson.length() - 1);
        }
        geojson.append("]}");

        // Salvar arquivo GeoJSON
        try (FileWriter writer = new FileWriter("severe_convection_pixels.geojson")) {
            writer.write(geojson.toString());
        }

        log.info("3 - GeoJSON com coordenadas em pixels gerado: severe_convection_pixels.geojson");
    }
	
	

    public static void processImage4() throws IOException {
        // Limites aproximados da imagem
        double minLat = -60.0, maxLat = 60.0;
        double minLon = -120.0, maxLon = -30.0;

        // Carregar imagem
        Mat image = Imgcodecs.imread("data/canal16_latest.jpg");
        int imgWidth = image.width();
        int imgHeight = image.height();

        // Converter para HSV
        Mat hsv = new Mat();
        Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);

        // Faixas HSV para vermelho e roxo (convecção severa)
        Scalar lowerRed1 = new Scalar(0, 80, 80);
        Scalar upperRed1 = new Scalar(10, 255, 255);
        Scalar lowerRed2 = new Scalar(160, 80, 80);
        Scalar upperRed2 = new Scalar(180, 255, 255);
        Scalar lowerPurple = new Scalar(125, 50, 50);
        Scalar upperPurple = new Scalar(155, 255, 255);

        // Criar máscara
        Mat mask = new Mat();
        Core.inRange(hsv, lowerRed1, upperRed1, mask);
        Mat temp = new Mat();
        Core.inRange(hsv, lowerRed2, upperRed2, temp);
        Core.bitwise_or(mask, temp, mask);
        Core.inRange(hsv, lowerPurple, upperPurple, temp);
        Core.bitwise_or(mask, temp, mask);

        // Encontrar contornos
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Criar GeoJSON
        StringBuilder geojson = new StringBuilder();
        geojson.append("{\"type\": \"FeatureCollection\", \"features\": [");

        int count = 0;
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > 20) {
                MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                MatOfPoint2f approxCurve = new MatOfPoint2f();
                Imgproc.approxPolyDP(contour2f, approxCurve, 0.001 * Imgproc.arcLength(contour2f, true), true);

                // Converter pixels para lat/lon (aproximação linear)
                StringBuilder coordsArray = new StringBuilder();
                coordsArray.append("[");
                Point[] points = approxCurve.toArray();
                for (int i = 0; i < points.length; i++) {
                    double lon = minLon + (points[i].x / imgWidth) * (maxLon - minLon);
                    double lat = maxLat - (points[i].y / imgHeight) * (maxLat - minLat);
                    coordsArray.append("[").append(lon).append(", ").append(lat).append("]");
                    if (i < points.length - 1) coordsArray.append(", ");
                }
                coordsArray.append("]");

                // Mensagem SIGMET
                String sigmetMsg = String.format(
                    "SIGMET %d - Convecção severa. Coordenadas aproximadas: %s. Altitude: FL390 (~12000 m).",
                    count, coordsArray.toString()
                );

                geojson.append("{\"type\": \"Feature\", \"geometry\": {\"type\": \"Polygon\", \"coordinates\": [")
                       .append(coordsArray)
                       .append("]}, \"properties\": {\"id\": ").append(count)
                       .append(", \"altitude_m\": 12000, \"risk\": \"ALTO\", \"sigmet\": \"")
                       .append(sigmetMsg.replace("\"", "\\\""))
                       .append("\"}},");
                count++;
            }
        }

        if (geojson.charAt(geojson.length() - 1) == ',') {
            geojson.deleteCharAt(geojson.length() - 1);
        }
        geojson.append("]}");

        // Salvar arquivo GeoJSON
        try (FileWriter writer = new FileWriter("severe_convection_approx.geojson")) {
            writer.write(geojson.toString());
        }

        log.info("GeoJSON gerado com aproximação rápida: severe_convection_approx.geojson");
    }

}
