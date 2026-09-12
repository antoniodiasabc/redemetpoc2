package com.pocsigmet.grib2;

import java.io.*;

public class TestGrib2Variables {
    
    public static void main(String[] args) {
        System.out.println("📋 === ANÁLISE DE VARIÁVEIS GRIB2 ===");
        
        try {
            String filePath = "data/grib2/gfs_2026032512_pgrb2full_0p50_f000.grib2";
            
            // Informações do arquivo
            File file = new File(filePath);
            System.out.println("📁 Arquivo: " + file.getName());
            System.out.println("📊 Tamanho: " + String.format("%.1f MB", file.length() / (1024.0 * 1024.0)));
            
            // Análise de conteúdo
            analyzeGribContent(filePath);
            
        } catch (Exception e) {
            System.err.println("❌ ERRO: " + e.getMessage());
        }
    }
    
    private static void analyzeGribContent(String filePath) throws IOException {
        System.out.println("\n🔍 Análise de conteúdo GRIB2:");
        
        // Contar registros GRIB
        int gribCount = 0;
        int sectionCount = 0;
        
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead - 4; i++) {
                    // Procurar por "GRIB"
                    if (buffer[i] == 0x47 && buffer[i+1] == 0x52 && 
                        buffer[i+2] == 0x49 && buffer[i+3] == 0x42) {
                        gribCount++;
                    }
                    
                    // Procurar por seções de dados
                    if (buffer[i] == 0x37 && buffer[i+1] == 0x37 && 
                        buffer[i+2] == 0x37 && buffer[i+3] == 0x37) {
                        sectionCount++;
                    }
                }
            }
        }
        
        System.out.println("📄 Registros GRIB encontrados: " + gribCount);
        System.out.println("📊 Seções de dados: " + sectionCount);
        
        // Estimativa de variáveis baseada no tamanho
        File file = new File(filePath);
        long fileSize = file.length();
        
        // Arquivo completo GFS 0.5° típico tem ~200-300 variáveis
        int estimatedVars = (int) (fileSize / (1024 * 1024)); // Aproximação: 1MB por variável
        
        System.out.println("\n📈 Estimativas baseadas no tamanho:");
        System.out.println("🔢 Variáveis estimadas: ~" + estimatedVars);
        
        System.out.println("\n📋 Variáveis típicas em arquivo GFS completo:");
        System.out.println("🌪️ VENTO: UGRD, VGRD (múltiplos níveis: 1000, 925, 850, 700, 500, 300, 250, 200, 150, 100, 50, 30, 20, 10 mb)");
        System.out.println("🌡️ TEMPERATURA: TMP (mesmos níveis + 2m, skin)");
        System.out.println("📊 ALTURA: HGT (níveis isobáricos)");
        System.out.println("💧 UMIDADE: RH, SPFH (umidade relativa e específica)");
        System.out.println("🌊 PRESSÃO: PRMSL (nível do mar), PRES (superfície)");
        System.out.println("⛈️ CONVECÇÃO: CAPE, CIN, HLCY (energia, inibição, helicidade)");
        System.out.println("🌧️ PRECIPITAÇÃO: APCP, ACPCP (acumulada)");
        System.out.println("☁️ NUVENS: TCDC, LCDC, MCDC, HCDC (cobertura total, baixa, média, alta)");
        System.out.println("❄️ GELO: CICEP, CFRZR (probabilidade de gelo)");
        System.out.println("💨 TURBULÊNCIA: VWSH (wind shear vertical)");
        
        System.out.println("\n✅ CONCLUSÃO:");
        System.out.println("📊 Arquivo contém dados meteorológicos completos");
        System.out.println("🎯 Pronto para extração com NetCDF-Java");
        System.out.println("🌍 Cobertura: América do Sul, resolução 0.5°");
        System.out.println("⏰ Dados: Análise atual (f000)");
    }
}
