package com.pocsigmet.grib2;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/grib2")
public class Grib2Controller {
    
    private final Grib2Downloader downloader;
    private final Grib2DataExtractor extractor;

    public Grib2Controller(Grib2Downloader downloader, Grib2DataExtractor extractor) {
        this.downloader = downloader;
        this.extractor = extractor;
    }
    
    @GetMapping("/wind/barbs/{level}")
    public ResponseEntity<String> getWindBarbs(@PathVariable String level) {
        try {
            String filePath = downloader.downloadGrib2FullFile("f000");
            List<Grib2DataExtractor.WindBarb> barbs = extractor.extractWindBarbs(filePath, level);
            String geoJson = extractor.convertToGeoJSON(barbs, "wind_barbs");
            
            return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body(geoJson);
                
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    @GetMapping("/info")
    public ResponseEntity<String> getGrib2Info() {
        return getGrib2InfoInternal();
    }
    
    @GetMapping("_info") 
    public ResponseEntity<String> getGrib2InfoLegacy() {
        return getGrib2InfoInternal();
    }
    
    private ResponseEntity<String> getGrib2InfoInternal() {
        try {
            String run = downloader.getLatestGFSRun();
            
            // Extrair data do run (formato: YYYYMMDDHH)
            String year = run.substring(0, 4);
            String month = run.substring(4, 6);
            String day = run.substring(6, 8);
            String hour = run.substring(8, 10);
            
            String response = String.format("{\"run\":\"%s\",\"date\":\"%s/%s/%s %s:00 UTC\"}", 
                run, day, month, year, hour);
            
            return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body(response);
                
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    @GetMapping("/wind/magnitude/{level}")
    public ResponseEntity<String> getWindMagnitude(@PathVariable String level) {
        try {
            String filePath = downloader.downloadGrib2FullFile("f000");
            List<Grib2DataExtractor.GridData> magnitude = extractor.extractWindMagnitude(filePath, level);
            String geoJson = extractor.convertToGeoJSON(magnitude, "wind_magnitude");
            
            return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body(geoJson);
                
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    @GetMapping("/cape")
    public ResponseEntity<String> getCAPE() {
        try {
            String filePath = downloader.downloadGrib2FullFile("f000");
            List<Grib2DataExtractor.GridData> cape = extractor.extractCAPE(filePath);
            String geoJson = extractor.convertToGeoJSON(cape, "cape");
            
            return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body(geoJson);
                
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    @GetMapping("/icing/{level}")
    public ResponseEntity<String> getIcing(@PathVariable String level) {
        try {
            String filePath = downloader.downloadGrib2FullFile("f000");
            List<Grib2DataExtractor.GridData> icing = extractor.extractIcing(filePath, level);
            String geoJson = extractor.convertToGeoJSON(icing, "icing");
            
            return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body(geoJson);
                
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    @GetMapping("/turbulence/{level}")
    public ResponseEntity<String> getTurbulence(@PathVariable String level) {
        try {
            String filePath = downloader.downloadGrib2FullFile("f000");
            List<Grib2DataExtractor.GridData> turbulence = extractor.extractTurbulence(filePath, level);
            String geoJson = extractor.convertToGeoJSON(turbulence, "turbulence");
            
            return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .header("Access-Control-Allow-Origin", "*")
                .body(geoJson);
                
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
