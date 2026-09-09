package com.pocsigmet.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;

import com.pocsigmet.service.RedemetService;
import com.pocsigmet.service.ImageProcessingService;
import com.pocsigmet.service.SigwxService;
import com.pocsigmet.grib2.Grib2Downloader;
import java.io.File;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class MeteorologicalController {
    
    @Autowired
    private RedemetService redemetService;
    
    @Autowired
    private ImageProcessingService imageProcessingService;

    @Autowired
    private SigwxService sigwxService;
    
    @GetMapping("/sigmets")
    public ResponseEntity<List<Map<String, Object>>> getAllSigmets() {
        try {
            CompletableFuture<List<Map<String, Object>>> future = redemetService.getAllSigmetsAsync();
            List<Map<String, Object>> sigmets = future.get();
            return ResponseEntity.ok(sigmets);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/sigmets/{fir}")
    public ResponseEntity<List<Map<String, Object>>> getSigmetsByFir(@PathVariable String fir) {
        List<Map<String, Object>> sigmets = redemetService.getSigmetsByFir(fir.toUpperCase());
        return ResponseEntity.ok(sigmets);
    }
    
    @GetMapping("/metar/{icao}")
    public ResponseEntity<Map<String, Object>> getMetar(@PathVariable String icao) {
        Map<String, Object> metar = redemetService.getMetarByIcao(icao.toUpperCase());
        if (metar.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(metar);
    }
    
    @GetMapping("/convection/analysis")
    public ResponseEntity<Map<String, Object>> getConvectionAnalysis() {
        try {
            Map<String, Object> analysis = imageProcessingService.getLatestConvectionAnalysis();
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/convection/process")
    public ResponseEntity<Map<String, Object>> processConvectionImage(@RequestParam String imagePath) {
        try {
            java.io.File allowed = new java.io.File("data").getAbsoluteFile();
            java.io.File requested = new java.io.File(imagePath).getCanonicalFile();
            if (!requested.toPath().startsWith(allowed.toPath())) return ResponseEntity.badRequest().build();
            Map<String, Object> result = imageProcessingService.processConvectionImage(imagePath);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping("/convection/process-transparent")
    public ResponseEntity<Map<String, String>> processTransparent() {
        try {
            String transparentPath = imageProcessingService.createTransparentVersion();
            return ResponseEntity.ok(Map.of(
                "original", imageProcessingService.getLatestImagePath(),
                "transparent", transparentPath
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", java.time.Instant.now().toString(),
            "service", "POC SIGMET Application"
        ));
    }

    @GetMapping("/sigwx/current")
    public ResponseEntity<String> getSigwxCurrent() {
        try {
            File f = sigwxService.resolveCurrentFile();
            if (f == null) return ResponseEntity.status(404).body("{\"error\":\"Nenhum arquivo SigWx disponível\"}");
            String geoJson = sigwxService.parseToGeoJson(f);
            return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(geoJson);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/sigwx/meta")
    public ResponseEntity<String> getSigwxMeta() {
        File f = sigwxService.resolveCurrentFile();
        if (f == null) return ResponseEntity.status(404).body("{\"error\":\"Nenhum arquivo SigWx disponível\"}");
        return ResponseEntity.ok()
            .header("Content-Type", "application/json")
            .body("{\"filename\":\"" + f.getName() + "\"}");
    }
}
