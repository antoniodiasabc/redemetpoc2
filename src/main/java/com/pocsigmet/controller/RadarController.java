package com.pocsigmet.controller;

import com.pocsigmet.service.RadarRedemtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/radar")
public class RadarController {

    @Autowired private RadarRedemtService radarService;

    @GetMapping("/latest")
    public ResponseEntity<?> latest() {
        return ResponseEntity.ok()
            .header("Cache-Control", "no-store")
            .body(radarService.getAll());
    }

    @GetMapping("/latest/{sigla}")
    public ResponseEntity<?> latestOne(@PathVariable String sigla) {
        Map<String, Object> r = radarService.getOne(sigla.toLowerCase());
        return r != null ? ResponseEntity.ok(r) : ResponseEntity.notFound().build();
    }

    @GetMapping("/history/{sigla}")
    public ResponseEntity<?> history(@PathVariable String sigla) {
        return ResponseEntity.ok(radarService.getHistory(sigla.toLowerCase()));
    }

    @GetMapping("/img")
    public void img(@RequestParam String url, jakarta.servlet.http.HttpServletResponse res) throws Exception {
        // só permite URLs da REDEMET
        if (!url.startsWith("https://redemet.decea.mil.br/")) {
            res.sendError(403); return;
        }
        java.net.URL u = new java.net.URL(url);
        try (var in = u.openStream()) {
            res.setContentType("image/png");
            res.setHeader("Cache-Control", "no-store");
            in.transferTo(res.getOutputStream());
        }
    }
}
