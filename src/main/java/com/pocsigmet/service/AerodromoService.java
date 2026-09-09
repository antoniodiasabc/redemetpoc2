package com.pocsigmet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pocsigmet.entity.Aerodromo;
import com.pocsigmet.repository.AerodromoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
// @Service — desabilitado, Oracle não está em uso

import java.util.Arrays;
import java.util.List;

// @Service — desabilitado, Oracle não está em uso
public class AerodromoService {
    private static final Logger log = LoggerFactory.getLogger(AerodromoService.class);
    
    // @Autowired — desabilitado, Oracle não está em uso
    private AerodromoRepository aerodromoRepository;
    
    // Buscar todos aeródromos do Brasil
    public List<Aerodromo> getAerodromosBrasil() {
        return aerodromoRepository.findByPais("BZ");
    }
    
    // Buscar aeródromos ativos do Brasil
    public List<Aerodromo> getAerodromosBrasilAtivos() {
        return aerodromoRepository.findByPaisAndAtivo("BZ", "A");
    }
    
    // Buscar aeródromos da América do Sul
    public List<Aerodromo> getAerodromosAmericaSul() {
        List<String> paisesSulAmericanos = Arrays.asList(
            "BZ", // Brasil
            "AR", // Argentina  
            "CL", // Chile
            "CO", // Colômbia
            "PE", // Peru
            "VE", // Venezuela
            "UY", // Uruguai
            "PY", // Paraguai
            "BO", // Bolívia
            "EC"  // Equador
        );
        return aerodromoRepository.findByPaisIn(paisesSulAmericanos);
    }
    
    // Buscar aeródromos por países específicos
    public List<Aerodromo> getAerodromosPorPaises(List<String> paises) {
        return aerodromoRepository.findByPaisIn(paises);
    }
    
    // Buscar aeródromos ativos por países
    public List<Aerodromo> getAerodromosAtivosPorPaises(List<String> paises) {
        return aerodromoRepository.findByPaisInAndAtivo(paises, "A");
    }
    
    // Buscar aeródromos em uma área geográfica
    public List<Aerodromo> getAerodromosPorBounds(Double latMin, Double latMax, Double lonMin, Double lonMax) {
        return aerodromoRepository.findByBounds(latMin, latMax, lonMin, lonMax);
    }
    
    // Buscar aeródromo específico por ICAO
    public Aerodromo getAerodromoPorIcao(String icao) {
        return aerodromoRepository.findByIcao(icao);
    }
    
    // Buscar primeiras 20 localidades SB do Brasil
    public List<Aerodromo> getTop20SBBrasil() {
        return aerodromoRepository.findTop20SBBrasil();
    }
    
    // Buscar localidades SB do Brasil
    public List<Aerodromo> getLocalidadesSBBrasil() {
        return aerodromoRepository.findByIcaoStartingWithAndPais("SB", "BZ");
    }
    
    // Buscar localidades SB ativas do Brasil
    public List<Aerodromo> getLocalidadesSBBrasilAtivas() {
        return aerodromoRepository.findByIcaoStartingWithAndPaisAndAtivo("SB", "BZ", "A");
    }
    
    // Contar aeródromos por país
    public long contarAerodromosPorPais(String pais) {
        return aerodromoRepository.countByPais(pais);
    }
    
    // Estatísticas
    public void imprimirEstatisticas() {
        log.info("📊 ESTATÍSTICAS AERÓDROMOS:");
        log.info("🇧🇷 Brasil: " + contarAerodromosPorPais("BZ"));
        log.info("🇦🇷 Argentina: " + contarAerodromosPorPais("AR"));
        log.info("🇨🇱 Chile: " + contarAerodromosPorPais("CL"));
        log.info("🇨🇴 Colômbia: " + contarAerodromosPorPais("CO"));
    }
}
