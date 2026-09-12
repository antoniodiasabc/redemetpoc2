package com.pocsigmet.grib2;

import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;
import ucar.ma2.Array;

public class TestSBSPFL500Wind {
    
    public static void main(String[] args) {
        System.out.println("✈️ === VENTO FL500 SBSP (CONGONHAS) ===");
        
        try {
            // 1. Usar arquivo de hoje já baixado
            System.out.println("\n1️⃣ Usando arquivo GRIB2 de hoje:");
            Grib2Downloader downloader = new Grib2Downloader();
            String gribFile = downloader.downloadGrib2FullFile("f000");
            System.out.println("📁 Arquivo: " + new java.io.File(gribFile).getName());
            
            try (NetcdfFile ncfile = NetcdfFiles.open(gribFile)) {
                
                // 2. Procurar variáveis de vento isobárico
                System.out.println("\n2️⃣ Procurando vento isobárico:");
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
                    System.out.println("❌ Variáveis de vento isobárico não encontradas");
                    return;
                }
                
                // 3. Verificar níveis isobáricos para FL500
                System.out.println("\n3️⃣ Procurando FL500 (100mb):");
                Variable isobaric = ncfile.findVariable("isobaric");
                if (isobaric != null) {
                    Array isoData = isobaric.read();
                    float[] levels = (float[]) isoData.copyTo1DJavaArray();
                    
                    // Procurar 100mb (FL500)
                    int level100Index = -1;
                    for (int i = 0; i < levels.length; i++) {
                        if (Math.abs(levels[i] - 10000) < 100) { // 100mb = 10000 Pa
                            level100Index = i;
                            System.out.println("✅ FL500 (100mb) encontrado no índice: " + i);
                            break;
                        }
                    }
                    
                    if (level100Index == -1) {
                        System.out.println("❌ FL500 (100mb) não encontrado");
                        System.out.println("📊 Níveis disponíveis:");
                        for (int i = 0; i < Math.min(10, levels.length); i++) {
                            System.out.println("   " + (int)(levels[i]/100) + " mb");
                        }
                        return;
                    }
                    
                    // 4. Extrair vento em SBSP FL500
                    System.out.println("\n4️⃣ Extraindo vento FL500 em SBSP:");
                    extractWindAtSBSP(ncfile, uWind, vWind, level100Index);
                }
                
            }
            
        } catch (Exception e) {
            System.err.println("❌ ERRO: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void extractWindAtSBSP(NetcdfFile ncfile, Variable uWind, Variable vWind, int levelIndex) {
        try {
            // Coordenadas de SBSP (Congonhas)
            double sbspLat = -23.6267;  // 23°37'36"S
            double sbspLon = -46.6556 + 360;  // 46°39'20"W → 313.34°E
            
            System.out.println("📍 SBSP (Congonhas): " + (-23.6267) + "°S, " + (-46.6556) + "°W");
            System.out.println("📍 Convertido: " + sbspLat + "°, " + sbspLon + "°E");
            
            // Obter coordenadas
            Variable latVar = ncfile.findVariable("lat");
            Variable lonVar = ncfile.findVariable("lon");
            
            Array latData = latVar.read();
            Array lonData = lonVar.read();
            
            float[] latArray = (float[]) latData.copyTo1DJavaArray();
            float[] lonArray = (float[]) lonData.copyTo1DJavaArray();
            
            // Encontrar índices mais próximos
            int latIndex = findNearestIndex(latArray, (float)sbspLat);
            int lonIndex = findNearestIndex(lonArray, (float)sbspLon);
            
            System.out.println("📍 Ponto da grade: " + latArray[latIndex] + "°, " + lonArray[lonIndex] + "°E");
            
            double distance = calculateDistance(-23.6267, -46.6556, 
                                             latArray[latIndex], 
                                             lonArray[lonIndex] > 180 ? lonArray[lonIndex] - 360 : lonArray[lonIndex]);
            System.out.println("📏 Distância: " + String.format("%.1f km", distance * 111));
            
            // Extrair dados de vento FL500
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
            
            System.out.println("\n🎯 VENTO FL500 SBSP:");
            System.out.println("=" + "=".repeat(50));
            System.out.println("✈️ AEROPORTO: SBSP (Congonhas - São Paulo)");
            System.out.println("📍 LOCALIZAÇÃO: " + String.format("%.2f°S, %.2f°W", Math.abs(latArray[latIndex]), Math.abs(lonArray[lonIndex] - 360)));
            System.out.println("🌪️ DIREÇÃO: " + String.format("%.0f°", direction));
            System.out.println("💨 VELOCIDADE: " + String.format("%.1f nós", speed));
            System.out.println("📊 Componentes: U=" + String.format("%.2f m/s", u) + ", V=" + String.format("%.2f m/s", v));
            System.out.println("🛩️ NÍVEL: FL500 (100mb - ~53.000ft)");
            System.out.println("⏰ HORÁRIO: " + getCurrentTime());
            System.out.println("=" + "=".repeat(50));
            
            // Interpretação para aviação
            String intensity = speed < 25 ? "FRACO" : speed < 50 ? "MODERADO" : 
                              speed < 80 ? "FORTE" : speed < 120 ? "MUITO FORTE" : "EXTREMO";
            String cardinalDir = getCardinalDirection(direction);
            
            System.out.println("\n✈️ INTERPRETAÇÃO AERONÁUTICA FL500:");
            System.out.println("🌪️ Vento " + intensity + " de " + cardinalDir);
            System.out.println("📋 Código: " + String.format("%03.0f/%03d", direction, (int)Math.round(speed)));
            
            if (speed > 80) {
                System.out.println("🚨 ALERTA: Vento muito forte - possível corrente de jato");
            } else if (speed > 50) {
                System.out.println("⚠️ ATENÇÃO: Vento forte em altitude - turbulência possível");
            }
            
            // Informações específicas FL500
            System.out.println("\n📋 INFORMAÇÕES FL500:");
            System.out.println("🏔️ Altitude: ~53.000 pés (16.154m)");
            System.out.println("🌡️ Temperatura típica: -65°C a -70°C");
            System.out.println("✈️ Nível de cruzeiro para jatos comerciais");
            
            if (speed > 100) {
                System.out.println("💨 Possível corrente de jato subtropical");
                System.out.println("⚡ Turbulência severa em cisalhamento");
            }
            
            // Análise operacional
            System.out.println("\n🛩️ ANÁLISE OPERACIONAL:");
            if (direction >= 180 && direction <= 360) {
                System.out.println("🌬️ Vento de componente oeste - favorável para voos E→W");
            } else {
                System.out.println("🌬️ Vento de componente leste - favorável para voos W→E");
            }
            
            double headwindComponent = speed * Math.cos(Math.toRadians(direction - 90)); // Assumindo rota E-W
            System.out.println("📈 Componente de proa (rota 090°): " + String.format("%.1f nós", headwindComponent));
            
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
