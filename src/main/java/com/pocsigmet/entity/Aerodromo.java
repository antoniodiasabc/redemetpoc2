package com.pocsigmet.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "TB_LOCALIDADE")
public class Aerodromo {
    
    @Id
    @Column(name = "CD_LOCALIDADE")
    private String icao;
    
    @Column(name = "NM_LOCALIDADE")
    private String nome;
    
    @Column(name = "LATITUDE")
    private Double latitude;
    
    @Column(name = "LONGITUDE")
    private Double longitude;
    
    @Column(name = "ID_GEOGRAFICO")
    private String pais;
    
    @Column(name = "STATUS")
    private String ativo;
    
    // Constructors
    public Aerodromo() {}
    
    public Aerodromo(String icao, String nome, Double latitude, Double longitude, String pais, String ativo) {
        this.icao = icao;
        this.nome = nome;
        this.latitude = latitude;
        this.longitude = longitude;
        this.pais = pais;
        this.ativo = ativo;
    }
    
    // Getters and Setters
    public String getIcao() { return icao; }
    public void setIcao(String icao) { this.icao = icao; }
    
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    
    public String getPais() { return pais; }
    public void setPais(String pais) { this.pais = pais; }
    
    public String getAtivo() { return ativo; }
    public void setAtivo(String ativo) { this.ativo = ativo; }
    
    @Override
    public String toString() {
        return String.format("Aerodromo{icao='%s', nome='%s', lat=%s, lon=%s, pais='%s'}", 
                           icao, nome, latitude, longitude, pais);
    }
}
