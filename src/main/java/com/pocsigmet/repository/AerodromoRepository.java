package com.pocsigmet.repository;

import com.pocsigmet.entity.Aerodromo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AerodromoRepository extends JpaRepository<Aerodromo, String> {
    
    // Buscar por país
    List<Aerodromo> findByPais(String pais);
    
    // Buscar por países (América do Sul)
    List<Aerodromo> findByPaisIn(List<String> paises);
    
    // Buscar ativos por país
    List<Aerodromo> findByPaisAndAtivo(String pais, String ativo);
    
    // Buscar ativos por países
    List<Aerodromo> findByPaisInAndAtivo(List<String> paises, String ativo);
    
    // Buscar por bounds geográficos
    @Query("SELECT a FROM Aerodromo a WHERE a.latitude BETWEEN :latMin AND :latMax AND a.longitude BETWEEN :lonMin AND :lonMax")
    List<Aerodromo> findByBounds(@Param("latMin") Double latMin, @Param("latMax") Double latMax, 
                                @Param("lonMin") Double lonMin, @Param("lonMax") Double lonMax);
    
    // Buscar por bounds e país
    @Query("SELECT a FROM Aerodromo a WHERE a.pais = :pais AND a.latitude BETWEEN :latMin AND :latMax AND a.longitude BETWEEN :lonMin AND :lonMax")
    List<Aerodromo> findByPaisAndBounds(@Param("pais") String pais, @Param("latMin") Double latMin, @Param("latMax") Double latMax, 
                                       @Param("lonMin") Double lonMin, @Param("lonMax") Double lonMax);
    
    // Contar por país
    long countByPais(String pais);
    
    // Buscar por ICAO específico
    Aerodromo findByIcao(String icao);
    
    // Buscar localidades que começam com SB (aeroportos brasileiros)
    List<Aerodromo> findByIcaoStartingWithAndPais(String prefix, String pais);
    
    // Buscar primeiras 20 localidades SB do Brasil
    @Query("SELECT a FROM Aerodromo a WHERE a.icao LIKE 'SB%' AND a.pais = 'BZ' AND ROWNUM <= 20")
    List<Aerodromo> findTop20SBBrasil();
    
    // Buscar localidades SB ativas do Brasil
    List<Aerodromo> findByIcaoStartingWithAndPaisAndAtivo(String prefix, String pais, String ativo);
}
