package com.pocsigmet.controller;

import com.pocsigmet.entity.Aerodromo;
import com.pocsigmet.service.AerodromoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @RestController — desabilitado, Oracle não está em uso
// @RequestMapping("/api/aerodromos") — desabilitado
public class AerodromoController {
    
    // @Autowired — desabilitado, Oracle não está em uso
    private AerodromoService aerodromoService;
    
    // Top 20 localidades SB do Brasil
    @GetMapping("/sb/top20")
    public List<Aerodromo> getTop20SB() {
        return aerodromoService.getTop20SBBrasil();
    }
    
    // Todas localidades SB do Brasil
    @GetMapping("/sb/brasil")
    public List<Aerodromo> getAllSBBrasil() {
        return aerodromoService.getLocalidadesSBBrasil();
    }
    
    // Localidades SB ativas do Brasil
    @GetMapping("/sb/brasil/ativas")
    public List<Aerodromo> getSBBrasilAtivas() {
        return aerodromoService.getLocalidadesSBBrasilAtivas();
    }
    
    // Todos aeródromos do Brasil
    @GetMapping("/brasil")
    public List<Aerodromo> getAllBrasil() {
        return aerodromoService.getAerodromosBrasil();
    }
    
    // Aeródromos ativos do Brasil
    @GetMapping("/brasil/ativas")
    public List<Aerodromo> getBrasilAtivas() {
        return aerodromoService.getAerodromosBrasilAtivos();
    }
    
    // Aeródromo específico por ICAO
    @GetMapping("/{icao}")
    public Aerodromo getByIcao(@PathVariable String icao) {
        return aerodromoService.getAerodromoPorIcao(icao);
    }
    
    // Estatísticas
    @GetMapping("/stats")
    public String getStats() {
        long totalBrasil = aerodromoService.contarAerodromosPorPais("BZ");
        return String.format("{\"brasil\": %d}", totalBrasil);
    }
}
