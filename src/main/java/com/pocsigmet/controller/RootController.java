package com.pocsigmet.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.pocsigmet.grib2.Grib2Downloader;

@RestController
@CrossOrigin(origins = "*")
public class RootController {
    
    private final Grib2Downloader downloader;

    public RootController(Grib2Downloader downloader) {
        this.downloader = downloader;
    }
    
    @GetMapping("/grib2_info")
    public ResponseEntity<String> getGrib2Info() {
        try {
            String run = downloader.getLatestGFSRun();
            
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
}
