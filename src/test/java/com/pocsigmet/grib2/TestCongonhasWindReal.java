package com.pocsigmet.grib2;

import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;
import ucar.ma2.Array;

public class TestCongonhasWindReal {
    
    public static void main(String[] args) {
        System.out.println("🌪️ === VENTO REAL CONGONHAS FL100 ===");
        
        try {
            String gribFile = "data/grib2/gfs_2026032512_pgrb2full_0p50_f000.grib2";
            
            try (NetcdfFile ncfile = NetcdfFiles.open(gribFile)) {
                
                // 1. Procurar variáveis UGRD e VGRD
                System.out.println("\n1️⃣ Procurando UGRD e VGRD:");
                Variable uWind = null, vWind = null;
                
                for (Variable var : ncfile.getVariables()) {
                    String name = var.getFullName();
                    if (name.contains("u-component_of_wind") && name.contains("isobaric")) {
                        uWind = var;
                        System.out.println("✅ U-wind: " + name + " - " + java.util.Arrays.toString(var.getShape()));
                    }
                    if (name.contains("v-component_of_wind") && name.contains("isobaric")) {
                        vWind = var;
                        System.out.println("✅ V-wind: " + name + " - " + java.util.Arrays.toString(var.getShape()));
                    }
                }
                
                if (uWind == null || vWind == null) {
                    System.out.println("❌ Variáveis de vento não encontradas");
                    System.out.println("🔍 Listando variáveis com 'wind':");
                    for (Variable var : ncfile.getVariables()) {
                        if (var.getFullName().toLowerCase().contains("wind")) {
                            System.out.println("   📄 " + var.getFullName());
                        }
                    }
                    return;
                }
                
                // 2. Verificar níveis isobáricos
                System.out.println("\n2️⃣ Verificando níveis:");
                Variable isobaric = ncfile.findVariable("isobaric");
                if (isobaric != null) {
                    Array isoData = isobaric.read();
                    float[] levels = (float[]) isoData.copyTo1DJavaArray();
                    
                    System.out.println("📊 Níveis disponíveis: " + levels.length);
                    for (int i = 0; i < Math.min(10, levels.length); i++) {
                        System.out.println("   " + (int)levels[i] + " mb");
                    }
                    
                    // Procurar 250mb
                    int level250Index = -1;
                    for (int i = 0; i < levels.length; i++) {
                        if (Math.abs(levels[i] - 25000) < 100) { // 250mb = 25000 Pa
                            level250Index = i;
                            System.out.println("✅ FL100 (250mb) encontrado no índice: " + i);
                            break;
                        }
                    }
                    
                    if (level250Index == -1) {
                        System.out.println("❌ FL100 (250mb) não encontrado");
                        return;
                    }
                    
                    // 3. Extrair vento em Congonhas
                    System.out.println("\n3️⃣ Extraindo vento em Congonhas:");
                    extractWindAtCongonhas(ncfile, uWind, vWind, level250Index);
                }
                
            }
            
        } catch (Exception e) {
            System.err.println("❌ ERRO: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void extractWindAtCongonhas(NetcdfFile ncfile, Variable uWind, Variable vWind, int levelIndex) {
        try {
            // Coordenadas de Congonhas (corrigidas)
            double congonhasLat = -23.6267;
            double congonhasLon = -46.6556 + 360; // 313.4°E
            
            // Obter coordenadas
            Variable latVar = ncfile.findVariable("lat");
            Variable lonVar = ncfile.findVariable("lon");
            
            Array latData = latVar.read();
            Array lonData = lonVar.read();
            
            float[] latArray = (float[]) latData.copyTo1DJavaArray();
            float[] lonArray = (float[]) lonData.copyTo1DJavaArray();
            
            // Encontrar índices mais próximos
            int latIndex = findNearestIndex(latArray, (float)congonhasLat);
            int lonIndex = findNearestIndex(lonArray, (float)congonhasLon);
            
            System.out.println("📍 Congonhas: " + congonhasLat + "°, " + congonhasLon + "°E");
            System.out.println("📍 Ponto da grade: " + latArray[latIndex] + "°, " + lonArray[lonIndex] + "°E");
            
            // Extrair dados de vento no ponto específico
            // Shape esperada: [time, level, lat, lon]
            int[] origin = {0, levelIndex, latIndex, lonIndex};
            int[] shape = {1, 1, 1, 1};
            
            Array uData = uWind.read(origin, shape);
            Array vData = vWind.read(origin, shape);
            
            float u = uData.getFloat(0);
            float v = vData.getFloat(0);
            
            // Calcular velocidade e direção
            double speed = Math.sqrt(u*u + v*v) * 1.94384; // m/s para nós
            double direction = Math.atan2(-u, -v) * 180.0 / Math.PI;
            if (direction < 0) direction += 360;
            
            System.out.println("\n🎯 VENTO REAL CONGONHAS FL100:");
            System.out.println("=" + "=".repeat(40));
            System.out.println("🌪️ DIREÇÃO: " + String.format("%.0f°", direction));
            System.out.println("💨 VELOCIDADE: " + String.format("%.1f nós", speed));
            System.out.println("📊 Componentes: U=" + String.format("%.2f m/s", u) + ", V=" + String.format("%.2f m/s", v));
            System.out.println("🛩️ NÍVEL: FL100 (250mb)");
            System.out.println("📍 LOCALIZAÇÃO: Congonhas (SBSP)");
            System.out.println("=" + "=".repeat(40));
            
            // Interpretação
            String intensity = speed < 10 ? "FRACO" : speed < 25 ? "MODERADO" : speed < 50 ? "FORTE" : "MUITO FORTE";
            String cardinalDir = getCardinalDirection(direction);
            
            System.out.println("\n✈️ INTERPRETAÇÃO AERONÁUTICA:");
            System.out.println("🌪️ Vento " + intensity + " de " + cardinalDir);
            System.out.println("📋 Código METAR: " + String.format("%03.0f%02.0f", direction, speed));
            
            if (speed > 30) {
                System.out.println("⚠️ ATENÇÃO: Vento forte - possível turbulência");
            }
            
        } catch (Exception e) {
            System.out.println("❌ Erro na extração: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static int findNearestIndex(float[] array, float target) {
        int bestIndex = 0;
        float minDiff = Math.abs(array[0] - target);
        
        for (int i = 1; i < array.length; i++) {
            float diff = Math.abs(array[i] - target);
            if (diff < minDiff) {
                minDiff = diff;
                bestIndex = i;
            }
        }
        
        return bestIndex;
    }
    
    private static String getCardinalDirection(double degrees) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", 
                              "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round(degrees / 22.5) % 16;
        return directions[index];
    }
}
