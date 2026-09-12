package com.pocsigmet.grib2;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TestCongonhasWgrib2 {
    
    // Coordenadas do Aeroporto de Congonhas (SBSP)
    private static final double CONGONHAS_LAT = -23.6267;
    private static final double CONGONHAS_LON = -46.6556;
    
    public static void main(String[] args) {
        System.out.println("🛠️ === TESTE CONGONHAS COM WGRIB2 ===");
        
        try {
            // 1. Verificar arquivo GRIB2
            String gribFile = "data/grib2/gfs_2026032512_pgrb2full_0p50_f000.grib2";
            File file = new File(gribFile);
            
            if (!file.exists()) {
                System.out.println("❌ Arquivo GRIB2 não encontrado: " + gribFile);
                return;
            }
            
            System.out.println("📁 Arquivo: " + file.getName());
            System.out.println("📊 Tamanho: " + String.format("%.1f MB", file.length() / (1024.0 * 1024.0)));
            
            // 2. Baixar wgrib2 se não existir
            downloadWgrib2();
            
            // 3. Listar todas as variáveis
            System.out.println("\n2️⃣ Listando variáveis com wgrib2...");
            listGribVariables(gribFile);
            
            // 4. Extrair dados de vento específicos
            System.out.println("\n3️⃣ Extraindo vento 850mb...");
            extractWindData(gribFile, "850 mb", "UGRD");
            extractWindData(gribFile, "850 mb", "VGRD");
            
            // 5. Gerar arquivo de controle com g2ctl
            System.out.println("\n4️⃣ Gerando arquivo de controle...");
            generateControlFile(gribFile);
            
        } catch (Exception e) {
            System.err.println("❌ ERRO: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void downloadWgrib2() {
        System.out.println("\n1️⃣ Verificando wgrib2...");
        
        try {
            // Tentar executar wgrib2
            Process process = Runtime.getRuntime().exec("wgrib2 -version");
            process.waitFor();
            
            if (process.exitValue() == 0) {
                System.out.println("✅ wgrib2 já instalado");
                return;
            }
        } catch (Exception e) {
            // wgrib2 não encontrado
        }
        
        System.out.println("⬇️ Baixando wgrib2...");
        try {
            // Baixar wgrib2 pré-compilado
            String[] commands = {
                "wget", "-q", 
                "https://www.ftp.cpc.ncep.noaa.gov/wd51we/wgrib2/wgrib2.tgz",
                "-O", "wgrib2.tgz"
            };
            
            Process process = Runtime.getRuntime().exec(commands);
            process.waitFor();
            
            if (process.exitValue() == 0) {
                System.out.println("✅ wgrib2 baixado");
            } else {
                System.out.println("⚠️ Usando análise alternativa");
            }
            
        } catch (Exception e) {
            System.out.println("⚠️ Erro ao baixar wgrib2: " + e.getMessage());
        }
    }
    
    private static void listGribVariables(String gribFile) {
        try {
            String[] command = {"wgrib2", "-s", gribFile};
            Process process = Runtime.getRuntime().exec(command);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int count = 0;
            
            System.out.println("📋 Primeiras 20 variáveis:");
            while ((line = reader.readLine()) != null && count < 20) {
                System.out.println("📄 " + line);
                count++;
            }
            
            // Contar total
            while ((line = reader.readLine()) != null) {
                count++;
            }
            
            System.out.println("📊 Total de registros: " + count);
            reader.close();
            process.waitFor();
            
        } catch (Exception e) {
            System.out.println("⚠️ wgrib2 não disponível: " + e.getMessage());
            alternativeAnalysis(gribFile);
        }
    }
    
    private static void extractWindData(String gribFile, String level, String variable) {
        try {
            String[] command = {"wgrib2", gribFile, "-match", variable, "-match", level, "-csv", "-"};
            Process process = Runtime.getRuntime().exec(command);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            List<String> windPoints = new ArrayList<>();
            
            while ((line = reader.readLine()) != null) {
                windPoints.add(line);
            }
            
            System.out.println("🌪️ " + variable + " " + level + ": " + windPoints.size() + " pontos");
            
            // Procurar ponto mais próximo de Congonhas
            findNearestPoint(windPoints, variable);
            
            reader.close();
            process.waitFor();
            
        } catch (Exception e) {
            System.out.println("⚠️ Erro ao extrair " + variable + ": " + e.getMessage());
        }
    }
    
    private static void findNearestPoint(List<String> points, String variable) {
        double minDistance = Double.MAX_VALUE;
        String nearestPoint = null;
        
        for (String point : points) {
            try {
                String[] parts = point.split(",");
                if (parts.length >= 4) {
                    double lat = Double.parseDouble(parts[1]);
                    double lon = Double.parseDouble(parts[2]);
                    double value = Double.parseDouble(parts[3]);
                    
                    double distance = calculateDistance(CONGONHAS_LAT, CONGONHAS_LON, lat, lon);
                    
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearestPoint = String.format("📍 %.2f°, %.2f° - %.1fkm - %s=%.2f", 
                                                    lat, lon, distance * 111, variable, value);
                    }
                }
            } catch (Exception e) {
                // Ignorar linhas malformadas
            }
        }
        
        if (nearestPoint != null) {
            System.out.println("🎯 Mais próximo: " + nearestPoint);
        }
    }
    
    private static void generateControlFile(String gribFile) {
        try {
            // Baixar g2ctl se não existir
            File g2ctl = new File("g2ctl.pl");
            if (!g2ctl.exists()) {
                String[] command = {"wget", "-q", 
                    "https://www.ftp.cpc.ncep.noaa.gov/wd51we/g2ctl/g2ctl.pl", 
                    "-O", "g2ctl.pl"};
                Process process = Runtime.getRuntime().exec(command);
                process.waitFor();
                
                if (process.exitValue() == 0) {
                    g2ctl.setExecutable(true);
                    System.out.println("✅ g2ctl.pl baixado");
                }
            }
            
            // Gerar arquivo .ctl
            String[] command = {"perl", "g2ctl.pl", gribFile};
            Process process = Runtime.getRuntime().exec(command);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            System.out.println("📄 Arquivo de controle (.ctl):");
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 10) {
                System.out.println(line);
                lineCount++;
            }
            
            reader.close();
            process.waitFor();
            
        } catch (Exception e) {
            System.out.println("⚠️ Erro ao gerar .ctl: " + e.getMessage());
        }
    }
    
    private static void alternativeAnalysis(String gribFile) {
        System.out.println("🔍 Análise alternativa do arquivo:");
        
        try {
            // Análise básica do arquivo
            File file = new File(gribFile);
            System.out.println("📊 Tamanho: " + file.length() + " bytes");
            
            // Contar registros GRIB
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4];
                int gribCount = 0;
                
                while (fis.read(buffer) != -1) {
                    if (buffer[0] == 0x47 && buffer[1] == 0x52 && 
                        buffer[2] == 0x49 && buffer[3] == 0x42) {
                        gribCount++;
                        fis.skip(100); // Pular para próximo registro
                    }
                }
                
                System.out.println("📄 Registros GRIB estimados: " + gribCount);
            }
            
        } catch (Exception e) {
            System.out.println("❌ Erro na análise: " + e.getMessage());
        }
    }
    
    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return c * 180 / Math.PI;
    }
}
