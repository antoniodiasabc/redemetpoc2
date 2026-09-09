package com.pocsigmet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SatelliteRiskGeoJSON {
    private static final Logger log = LoggerFactory.getLogger(SatelliteRiskGeoJSON.class);
    static { nu.pattern.OpenCV.loadLocally(); }

    public static void recebonovoArquivo(String path) throws IOException {
        // Limites geográficos da imagem
        double minLat = -60.0, maxLat = 60.0;
        double minLon = -120.0, maxLon = -30.0;

        // Carregar imagem
        Mat image = Imgcodecs.imread(path);
        int imgWidth = image.width();
        int imgHeight = image.height();

        // Converter para HSV
        Mat hsv = new Mat();
        Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);

        // Definir faixas de cores
        Scalar lowerRed1 = new Scalar(0, 100, 100);
        Scalar upperRed1 = new Scalar(10, 255, 255);
        Scalar lowerRed2 = new Scalar(160, 100, 100);
        Scalar upperRed2 = new Scalar(180, 255, 255);
        Scalar lowerYellow = new Scalar(20, 100, 100);
        Scalar upperYellow = new Scalar(30, 255, 255);
        Scalar lowerGreen = new Scalar(40, 50, 50);
        Scalar upperGreen = new Scalar(80, 255, 255);

        // Criar máscara
        Mat mask = new Mat();
        Core.inRange(hsv, lowerRed1, upperRed1, mask);
        Mat temp = new Mat();
        Core.inRange(hsv, lowerRed2, upperRed2, temp);
        Core.bitwise_or(mask, temp, mask);
        Core.inRange(hsv, lowerYellow, upperYellow, temp);
        Core.bitwise_or(mask, temp, mask);
        Core.inRange(hsv, lowerGreen, upperGreen, temp);
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
            if (area > 100) {
                MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                MatOfPoint2f approxCurve = new MatOfPoint2f();
                Imgproc.approxPolyDP(contour2f, approxCurve, 0.005 * Imgproc.arcLength(contour2f, true), true);

                // Determinar altitude pela cor média do contorno
                Rect boundingBox = Imgproc.boundingRect(contour);
                Mat roi = hsv.submat(boundingBox);
                Scalar meanColor = Core.mean(roi);
                double hue = meanColor.val[0];
                double altitude;
                if (hue < 15 || hue > 160) {
                    altitude = 12000; // vermelho -> nuvem alta
                } else if (hue >= 20 && hue <= 30) {
                    altitude = 8000; // amarelo -> média
                } else {
                    altitude = 4000; // verde -> baixa
                }

                geojson.append("{\"type\": \"Feature\", \"geometry\": {\"type\": \"Polygon\", \"coordinates\": [[");
                Point[] points = approxCurve.toArray();
                for (int i = 0; i < points.length; i++) {
                    double lon = minLon + (points[i].x / imgWidth) * (maxLon - minLon);
                    double lat = maxLat - (points[i].y / imgHeight) * (maxLat - minLat);
                    geojson.append("[").append(lon).append(", ").append(lat).append("]");
                    if (i < points.length - 1) geojson.append(", ");
                }
                geojson.append("]]}, \"properties\": {\"id\": ").append(count++)
                        .append(", \"altitude_m\": ").append(altitude).append("}},");
            }
        }

        if (geojson.charAt(geojson.length() - 1) == ',') {
            geojson.deleteCharAt(geojson.length() - 1);
        }
        geojson.append("]}");

        try (FileWriter writer = new FileWriter("data/config/risk_polygons.geojson")) {
            writer.write(geojson.toString());
        }

        log.info("GeoJSON com altitude gerado: data/config/risk_polygons.geojson");
    }
}
