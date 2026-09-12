package com.pocsigmet.grib2;

import com.pocsigmet.grib2.WindBarbData;
import com.pocsigmet.grib2.WindBarbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wind")
public class WindBarbController {
    
    @Autowired
    private WindBarbService windBarbService;
    
    @GetMapping("/barbs/{level}")
    public ResponseEntity<List<WindBarbData>> getWindBarbs(@PathVariable String level,
                                                           @RequestParam(defaultValue = "1") int skip) {
        try {
            List<WindBarbData> windBarbs = windBarbService.getWindBarbs(level, skip);
            return ResponseEntity.ok(windBarbs);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
