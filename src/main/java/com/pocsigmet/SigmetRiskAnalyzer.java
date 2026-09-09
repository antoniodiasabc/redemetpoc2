package com.pocsigmet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class SigmetRiskAnalyzer {
    
    public String processImage() throws IOException {
        // Verificar se existe GeoJSON de polígonos de risco
        File geojsonFile = new File("risk_polygons.geojson");
        
        if (geojsonFile.exists()) {
            // Retornar o conteúdo do GeoJSON
            return Files.readString(geojsonFile.toPath());
        } else {
            // Retornar GeoJSON vazio se não houver dados
            return "{\"type\": \"FeatureCollection\", \"features\": []}";
        }
    }
}
