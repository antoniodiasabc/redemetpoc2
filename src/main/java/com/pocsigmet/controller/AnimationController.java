package com.pocsigmet.controller;

import com.pocsigmet.grib2.WindBarbService;
import com.pocsigmet.grib2.Grib2Downloader;
import com.pocsigmet.grib2.WindBarbData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/v1/animation")
public class AnimationController {

    private final WindBarbService windBarbService;
    private final Grib2Downloader grib2Downloader;

    public AnimationController(WindBarbService windBarbService, Grib2Downloader grib2Downloader) {
        this.windBarbService = windBarbService;
        this.grib2Downloader = grib2Downloader;
    }

    @GetMapping("/satellite-wind")
    public ResponseEntity<Map<String, Object>> getSatelliteWindAnimation(
            @RequestParam(defaultValue = "6") int hours) {
        
        try {
            // Buscar imagens de satélite recentes
            List<String> satelliteImages = getRecentSatelliteImages(hours);
            
            // Buscar dados de vento para o mesmo período
            List<Map<String, Object>> windData = getWindDataForTimeRange(hours);
            
            Map<String, Object> response = new HashMap<>();
            response.put("satellite_frames", satelliteImages);
            response.put("wind_overlays", windData);
            response.put("frame_count", Math.min(satelliteImages.size(), windData.size()));
            response.put("interval_minutes", 30); // Intervalo entre frames
            response.put("generated_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Erro ao gerar animação");
            error.put("message", e.getMessage());
            error.put("frame_count", 0);
            return ResponseEntity.ok(error);
        }
    }

    private List<String> getRecentSatelliteImages(int hours) {
        List<String> images = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        // Gerar URLs das últimas imagens (simulando intervalos de 30min)
        for (int i = 0; i < hours * 2; i++) {
            LocalDateTime time = now.minusMinutes(i * 30);
            String timestamp = time.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            images.add("/cptec/canal16/image_" + timestamp);
        }
        
        // Reverter para ordem cronológica
        Collections.reverse(images);
        return images;
    }

    private List<Map<String, Object>> getWindDataForTimeRange(int hours) {
        List<Map<String, Object>> windFrames = new ArrayList<>();
        
        try {
            // Usar o WindBarbService injetado para obter dados de vento
            List<WindBarbData> currentWindData = windBarbService.getWindBarbs("surface", 3);
            
            // Simular dados históricos baseados no atual
            for (int i = 0; i < hours * 2; i++) {
                Map<String, Object> frame = new HashMap<>();
                frame.put("timestamp", LocalDateTime.now().minusMinutes(i * 30).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                
                // Converter WindBarbData para formato JSON
                List<Map<String, Object>> windBarbs = new ArrayList<>();
                for (WindBarbData barb : currentWindData) {
                    Map<String, Object> barbData = new HashMap<>();
                    barbData.put("lat", barb.getLat());
                    barbData.put("lon", barb.getLon());
                    barbData.put("speed", barb.getSpeed());
                    barbData.put("direction", barb.getDirection());
                    windBarbs.add(barbData);
                }
                
                frame.put("wind_barbs", windBarbs);
                windFrames.add(frame);
            }
            
            Collections.reverse(windFrames);
            
        } catch (Exception e) {
            // Fallback: frame vazio se houver erro
            Map<String, Object> emptyFrame = new HashMap<>();
            emptyFrame.put("wind_barbs", new ArrayList<>());
            emptyFrame.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            windFrames.add(emptyFrame);
        }
        
        return windFrames;
    }
}
