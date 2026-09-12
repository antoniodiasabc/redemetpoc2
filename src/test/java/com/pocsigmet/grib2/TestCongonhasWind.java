package com.pocsigmet.grib2;

import java.util.List;

public class TestCongonhasWind {
    
    // Coordenadas do Aeroporto de Congonhas (SBSP)
    private static final double CONGONHAS_LAT = -23.6267;
    private static final double CONGONHAS_LON = -46.6556;
    private static final double SEARCH_RADIUS = 10.0; // 10 graus de raio (~1100km)
    
    public static void main(String[] args) {
        System.out.println("✈️ === VENTO EM CONGONHAS FL100 ===");
        
        try {
            // 1. Baixar dados GRIB2
            System.out.println("\n1️⃣ Obtendo dados meteorológicos...");
            Grib2Downloader downloader = new Grib2Downloader();
            Grib2DataExtractor extractor = new Grib2DataExtractor();
            
            String filePath = downloader.downloadGrib2FullFile("f000");
            System.out.println("📁 Arquivo: " + new java.io.File(filePath).getName());
            
            // 2. Extrair dados de vento FL100 (250mb)
            System.out.println("\n2️⃣ Extraindo vento FL100 (250mb)...");
            List<Grib2DataExtractor.WindBarb> windData = extractor.extractWindBarbs(filePath, "250_mb");
            
            System.out.println("🌪️ Total de pontos de vento: " + windData.size());
            
            // 3. Encontrar vento mais próximo de Congonhas
            System.out.println("\n3️⃣ Procurando vento próximo a Congonhas...");
            System.out.println("📍 Congonhas: " + CONGONHAS_LAT + "°S, " + CONGONHAS_LON + "°W");
            
            Grib2DataExtractor.WindBarb nearestWind = null;
            double minDistance = Double.MAX_VALUE;
            
            for (Grib2DataExtractor.WindBarb wind : windData) {
                double distance = calculateDistance(CONGONHAS_LAT, CONGONHAS_LON, wind.lat, wind.lon);
                
                if (distance < minDistance && distance <= SEARCH_RADIUS) {
                    minDistance = distance;
                    nearestWind = wind;
                }
            }
            
            // 4. Mostrar resultado
            if (nearestWind != null) {
                System.out.println("\n🎯 VENTO ENCONTRADO:");
                System.out.println("=" + "=".repeat(50));
                System.out.println("📍 Posição: " + String.format("%.2f°, %.2f°", nearestWind.lat, nearestWind.lon));
                System.out.println("📏 Distância de Congonhas: " + String.format("%.1f km", minDistance * 111));
                System.out.println("🌪️ DIREÇÃO: " + String.format("%.0f°", nearestWind.direction));
                System.out.println("💨 INTENSIDADE: " + String.format("%.1f nós", nearestWind.speed));
                System.out.println("📊 Componentes: U=" + String.format("%.1f", nearestWind.u) + " m/s, V=" + String.format("%.1f", nearestWind.v) + " m/s");
                System.out.println("🛩️ NÍVEL: FL100 (250mb)");
                System.out.println("=" + "=".repeat(50));
                
                // 5. Interpretação para aviação
                System.out.println("\n✈️ INTERPRETAÇÃO PARA AVIAÇÃO:");
                interpretWindForAviation(nearestWind);
                
            } else {
                System.out.println("\n❌ Nenhum dado de vento encontrado próximo a Congonhas");
                System.out.println("💡 Raio de busca: " + SEARCH_RADIUS + "° (~" + (SEARCH_RADIUS * 111) + "km)");
                
                // Mostrar pontos mais próximos disponíveis
                System.out.println("\n📊 Pontos de vento mais próximos:");
                windData.stream()
                    .sorted((w1, w2) -> Double.compare(
                        calculateDistance(CONGONHAS_LAT, CONGONHAS_LON, w1.lat, w1.lon),
                        calculateDistance(CONGONHAS_LAT, CONGONHAS_LON, w2.lat, w2.lon)
                    ))
                    .limit(3)
                    .forEach(wind -> {
                        double dist = calculateDistance(CONGONHAS_LAT, CONGONHAS_LON, wind.lat, wind.lon);
                        System.out.println(String.format("📍 %.2f°, %.2f° - %.0fkm - %.0f°/%.1fkt", 
                            wind.lat, wind.lon, dist * 111, wind.direction, wind.speed));
                    });
            }
            
        } catch (Exception e) {
            System.err.println("\n❌ ERRO: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Fórmula de Haversine simplificada para distâncias pequenas
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return c * 180 / Math.PI; // Retorna em graus
    }
    
    private static void interpretWindForAviation(Grib2DataExtractor.WindBarb wind) {
        double speed = wind.speed;
        double direction = wind.direction;
        
        // Classificação da intensidade
        String intensity;
        if (speed < 10) intensity = "FRACO";
        else if (speed < 25) intensity = "MODERADO";
        else if (speed < 50) intensity = "FORTE";
        else intensity = "MUITO FORTE";
        
        // Direção cardeal
        String cardinalDir = getCardinalDirection(direction);
        
        System.out.println("🌪️ Vento " + intensity + " de " + cardinalDir);
        System.out.println("📋 Código METAR: " + String.format("%03.0f%02.0f", direction, speed));
        
        if (speed > 30) {
            System.out.println("⚠️ ATENÇÃO: Vento forte - possível turbulência");
        }
        
        if (speed > 50) {
            System.out.println("🚨 ALERTA: Vento muito forte - condições severas");
        }
    }
    
    private static String getCardinalDirection(double degrees) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", 
                              "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) Math.round(degrees / 22.5) % 16;
        return directions[index];
    }
}
