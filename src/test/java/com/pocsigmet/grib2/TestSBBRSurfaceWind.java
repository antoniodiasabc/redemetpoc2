package com.pocsigmet.grib2;

import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;
import ucar.ma2.Array;

public class TestSBBRSurfaceWind {
    
    public static void main(String[] args) {
        System.out.println("🛩️ === VENTO SUPERFÍCIE SBBR (BRASÍLIA) ===");
        
        try {
            // 1. Baixar arquivo de hoje
            System.out.println("\n1️⃣ Baixando arquivo GRIB2 de hoje:");
            Grib2Downloader downloader = new Grib2Downloader();
            String gribFile = downloader.downloadGrib2FullFile("f000");
            System.out.println("📁 Arquivo: " + new java.io.File(gribFile).getName());
            
            try (NetcdfFile ncfile = NetcdfFiles.open(gribFile)) {
                
                // 2. Procurar variáveis de vento de superfície
                System.out.println("\n2️⃣ Procurando vento de superfície:");
                Variable uWind10m = null, vWind10m = null;
                
                for (Variable var : ncfile.getVariables()) {
                    String name = var.getFullName();
                    if (name.contains("u-component_of_wind") && name.contains("height_above_ground")) {
                        uWind10m = var;
                        System.out.println("✅ U-wind 10m: " + name);
                    }
                    if (name.contains("v-component_of_wind") && name.contains("height_above_ground")) {
                        vWind10m = var;
                        System.out.println("✅ V-wind 10m: " + name);
                    }
                }
                
                if (uWind10m == null || vWind10m == null) {
                    System.out.println("❌ Variáveis de vento de superfície não encontradas");
                    System.out.println("🔍 Listando variáveis com 'wind':");
                    for (Variable var : ncfile.getVariables()) {
                        if (var.getFullName().toLowerCase().contains("wind")) {
                            System.out.println("   📄 " + var.getFullName());
                        }
                    }
                    return;
                }
                
                // 3. Extrair vento em SBBR
                System.out.println("\n3️⃣ Extraindo vento em SBBR:");
                extractWindAtSBBR(ncfile, uWind10m, vWind10m);
                
            }
            
        } catch (Exception e) {
            System.err.println("❌ ERRO: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void extractWindAtSBBR(NetcdfFile ncfile, Variable uWind, Variable vWind) {
        try {
            // Coordenadas de SBBR (Brasília)
            double sbbrLat = -15.8711;  // 15°52'16"S
            double sbbrLon = -47.9186 + 360;  // 47°55'07"W → 312.08°E
            
            System.out.println("📍 SBBR (Brasília): " + (-15.8711) + "°S, " + (-47.9186) + "°W");
            System.out.println("📍 Convertido: " + sbbrLat + "°, " + sbbrLon + "°E");
            
            // Obter coordenadas
            Variable latVar = ncfile.findVariable("lat");
            Variable lonVar = ncfile.findVariable("lon");
            
            Array latData = latVar.read();
            Array lonData = lonVar.read();
            
            float[] latArray = (float[]) latData.copyTo1DJavaArray();
            float[] lonArray = (float[]) lonData.copyTo1DJavaArray();
            
            // Encontrar índices mais próximos
            int latIndex = findNearestIndex(latArray, (float)sbbrLat);
            int lonIndex = findNearestIndex(lonArray, (float)sbbrLon);
            
            System.out.println("📍 Ponto da grade: " + latArray[latIndex] + "°, " + lonArray[lonIndex] + "°E");
            
            double distance = calculateDistance(-15.8711, -47.9186, 
                                             latArray[latIndex], 
                                             lonArray[lonIndex] > 180 ? lonArray[lonIndex] - 360 : lonArray[lonIndex]);
            System.out.println("📏 Distância: " + String.format("%.1f km", distance * 111));
            
            // Extrair dados de vento de superfície (10m)
            // Shape esperada: [time, height_above_ground, lat, lon]
            int[] origin = {0, 0, latIndex, lonIndex};  // height_above_ground[0] = 10m
            int[] shape = {1, 1, 1, 1};
            
            Array uData = uWind.read(origin, shape);
            Array vData = vWind.read(origin, shape);
            
            float u = uData.getFloat(0);
            float v = vData.getFloat(0);
            
            // Calcular velocidade e direção
            double speed = Math.sqrt(u*u + v*v) * 1.94384; // m/s para nós
            double direction = Math.atan2(-u, -v) * 180.0 / Math.PI;
            if (direction < 0) direction += 360;
            
            System.out.println("\n🎯 VENTO DE SUPERFÍCIE SBBR:");
            System.out.println("=" + "=".repeat(45));
            System.out.println("🛩️ AEROPORTO: SBBR (Brasília - Presidente Juscelino Kubitschek)");
            System.out.println("📍 LOCALIZAÇÃO: " + String.format("%.2f°S, %.2f°W", Math.abs(latArray[latIndex]), Math.abs(lonArray[lonIndex] - 360)));
            System.out.println("🌪️ DIREÇÃO: " + String.format("%.0f°", direction));
            System.out.println("💨 VELOCIDADE: " + String.format("%.1f nós", speed));
            System.out.println("📊 Componentes: U=" + String.format("%.2f m/s", u) + ", V=" + String.format("%.2f m/s", v));
            System.out.println("🏔️ NÍVEL: Superfície (10m AGL)");
            System.out.println("⏰ HORÁRIO: " + getCurrentTime());
            System.out.println("=" + "=".repeat(45));
            
            // Interpretação
            String intensity = speed < 5 ? "CALMO" : speed < 10 ? "FRACO" : 
                              speed < 20 ? "MODERADO" : speed < 35 ? "FORTE" : "MUITO FORTE";
            String cardinalDir = getCardinalDirection(direction);
            
            System.out.println("\n✈️ INTERPRETAÇÃO AERONÁUTICA:");
            System.out.println("🌪️ Vento " + intensity + " de " + cardinalDir);
            System.out.println("📋 Código METAR: " + String.format("%03.0f%02d", direction, (int)Math.round(speed)));
            
            if (speed < 3) {
                System.out.println("🌀 Condição: CALMO (00000)");
            } else if (speed > 25) {
                System.out.println("⚠️ ATENÇÃO: Vento forte para operações de superfície");
            }
            
            // Informações adicionais sobre SBBR
            System.out.println("\n📋 INFORMAÇÕES SBBR:");
            System.out.println("🛬 Pistas: 11L/29R (3200m), 11R/29L (3300m)");
            System.out.println("🏔️ Elevação: 1061m (3481ft)");
            System.out.println("🌡️ Altitude densidade alta - performance reduzida");
            
            if (direction >= 290 || direction <= 110) {
                System.out.println("✅ Vento favorável para pistas 11L/R");
            } else {
                System.out.println("⚠️ Vento de través/cauda para pistas 11L/R");
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
    
    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return c * 180 / Math.PI;
    }
    
    private static String getCardinalDirection(double degrees) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", 
                              "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round(degrees / 22.5) % 16;
        return directions[index];
    }
    
    private static String getCurrentTime() {
        return java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        ) + " UTC";
    }
}
