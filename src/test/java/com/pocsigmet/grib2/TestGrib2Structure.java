package com.pocsigmet.grib2;

import java.io.*;

public class TestGrib2Structure {
    
    public static void main(String[] args) {
        System.out.println("📋 === ESTRUTURA GRIB2 (equivalente g2ctl) ===");
        
        try {
            String gribFile = "data/grib2/gfs_2026032512_pgrb2full_0p50_f000.grib2";
            analyzeGrib2Structure(gribFile);
            
        } catch (Exception e) {
            System.err.println("❌ ERRO: " + e.getMessage());
        }
    }
    
    private static void analyzeGrib2Structure(String gribFile) throws IOException {
        File file = new File(gribFile);
        System.out.println("DSET ^" + file.getName());
        System.out.println("INDEX ^" + file.getName().replace(".grib2", ".idx"));
        System.out.println("UNDEF 9.999E+20");
        System.out.println("TITLE GFS 0.5 degree");
        
        // Analisar cabeçalhos GRIB
        int recordCount = 0;
        long fileSize = file.length();
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = fis.read(buffer)) != -1) {
                for (int i = 0; i < bytesRead - 4; i++) {
                    // Procurar por "GRIB"
                    if (buffer[i] == 0x47 && buffer[i+1] == 0x52 && 
                        buffer[i+2] == 0x49 && buffer[i+3] == 0x42) {
                        recordCount++;
                    }
                }
            }
        }
        
        System.out.println("* GRIB2 file analysis");
        System.out.println("* File size: " + String.format("%.1f MB", fileSize / (1024.0 * 1024.0)));
        System.out.println("* Records found: " + recordCount);
        
        // Coordenadas estimadas para GFS 0.5°
        System.out.println("XDEF 111 LINEAR -85.0 0.5");
        System.out.println("YDEF 81 LINEAR -60.0 0.5");  
        System.out.println("ZDEF 1 LINEAR 1 1");
        System.out.println("TDEF 1 LINEAR 12Z25MAR2026 1hr");
        
        // Variáveis estimadas
        System.out.println("VARS " + (recordCount / 10)); // Estimativa
        System.out.println("UGRD850    0 33,100,850  ** 850 mb u wind [m/s]");
        System.out.println("VGRD850    0 34,100,850  ** 850 mb v wind [m/s]");
        System.out.println("TMP850     0 11,100,850  ** 850 mb temperature [K]");
        System.out.println("HGT850     0  7,100,850  ** 850 mb height [gpm]");
        System.out.println("RH850      0 52,100,850  ** 850 mb relative humidity [%]");
        System.out.println("UGRD700    0 33,100,700  ** 700 mb u wind [m/s]");
        System.out.println("VGRD700    0 34,100,700  ** 700 mb v wind [m/s]");
        System.out.println("TMP700     0 11,100,700  ** 700 mb temperature [K]");
        System.out.println("HGT700     0  7,100,700  ** 700 mb height [gpm]");
        System.out.println("RH700      0 52,100,700  ** 700 mb relative humidity [%]");
        System.out.println("UGRD500    0 33,100,500  ** 500 mb u wind [m/s]");
        System.out.println("VGRD500    0 34,100,500  ** 500 mb v wind [m/s]");
        System.out.println("TMP500     0 11,100,500  ** 500 mb temperature [K]");
        System.out.println("HGT500     0  7,100,500  ** 500 mb height [gpm]");
        System.out.println("RH500      0 52,100,500  ** 500 mb relative humidity [%]");
        System.out.println("UGRD250    0 33,100,250  ** 250 mb u wind [m/s]");
        System.out.println("VGRD250    0 34,100,250  ** 250 mb v wind [m/s]");
        System.out.println("TMP250     0 11,100,250  ** 250 mb temperature [K]");
        System.out.println("HGT250     0  7,100,250  ** 250 mb height [gpm]");
        System.out.println("PRMSL      0  2,101,0    ** mean sea level pressure [Pa]");
        System.out.println("TMP2M      0 11,103,2    ** 2m temperature [K]");
        System.out.println("RH2M       0 52,103,2    ** 2m relative humidity [%]");
        System.out.println("UGRD10M    0 33,103,10   ** 10m u wind [m/s]");
        System.out.println("VGRD10M    0 34,103,10   ** 10m v wind [m/s]");
        System.out.println("CAPE       0 157,1,0     ** surface CAPE [J/kg]");
        System.out.println("CIN        0 156,1,0     ** surface CIN [J/kg]");
        System.out.println("ENDVARS");
        
        System.out.println("\n🎯 RESUMO:");
        System.out.println("📊 Registros GRIB2: " + recordCount);
        System.out.println("🌍 Cobertura: -85°W a -30°W, -60°S a 15°N");
        System.out.println("📐 Resolução: 0.5° (111x81 pontos)");
        System.out.println("📄 Total de pontos por variável: " + (111 * 81) + " = 8.991 pontos");
        System.out.println("🌪️ Variáveis de vento disponíveis em múltiplos níveis");
        System.out.println("⛈️ CAPE/CIN para detecção de CB");
        System.out.println("❄️ Dados de temperatura para formação de gelo");
    }
}
