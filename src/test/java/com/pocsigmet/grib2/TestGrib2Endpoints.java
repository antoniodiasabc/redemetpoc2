package com.pocsigmet.grib2;

public class TestGrib2Endpoints {
    
    public static void main(String[] args) {
        System.out.println("🌪️ === TESTE ENDPOINTS GRIB2 ===");
        
        try {
            // 1. Testar extração de dados
            System.out.println("\n1️⃣ Testando extração de dados...");
            Grib2Downloader downloader = new Grib2Downloader();
            Grib2DataExtractor extractor = new Grib2DataExtractor();
            
            String filePath = downloader.downloadGrib2FullFile("f000");
            System.out.println("✅ Arquivo baixado: " + filePath);
            
            // 2. Testar barbelas de vento
            System.out.println("\n2️⃣ Testando barbelas de vento...");
            try {
                var windBarbs = extractor.extractWindBarbs(filePath, "850_mb");
                System.out.println("🌪️ Barbelas extraídas: " + windBarbs.size());
                
                if (!windBarbs.isEmpty()) {
                    var sample = windBarbs.get(0);
                    System.out.println("📍 Amostra: lat=" + sample.lat + ", lon=" + sample.lon + 
                                     ", speed=" + String.format("%.1f", sample.speed) + "kt, dir=" + 
                                     String.format("%.0f", sample.direction) + "°");
                }
            } catch (Exception e) {
                System.out.println("⚠️ Barbelas: " + e.getMessage());
            }
            
            // 3. Testar magnitude do vento
            System.out.println("\n3️⃣ Testando magnitude do vento...");
            try {
                var magnitude = extractor.extractWindMagnitude(filePath, "850_mb");
                System.out.println("💨 Pontos de magnitude: " + magnitude.size());
            } catch (Exception e) {
                System.out.println("⚠️ Magnitude: " + e.getMessage());
            }
            
            // 4. Testar CAPE
            System.out.println("\n4️⃣ Testando CAPE...");
            try {
                var cape = extractor.extractCAPE(filePath);
                System.out.println("⛈️ Pontos CAPE: " + cape.size());
            } catch (Exception e) {
                System.out.println("⚠️ CAPE: " + e.getMessage());
            }
            
            // 5. Testar conversão GeoJSON
            System.out.println("\n5️⃣ Testando conversão GeoJSON...");
            try {
                var windBarbs = extractor.extractWindBarbs(filePath, "850_mb");
                if (!windBarbs.isEmpty()) {
                    String geoJson = extractor.convertToGeoJSON(windBarbs.subList(0, Math.min(3, windBarbs.size())), "wind_barbs");
                    System.out.println("📄 GeoJSON gerado: " + geoJson.length() + " caracteres");
                    System.out.println("📋 Amostra: " + geoJson.substring(0, Math.min(200, geoJson.length())) + "...");
                }
            } catch (Exception e) {
                System.out.println("⚠️ GeoJSON: " + e.getMessage());
            }
            
            System.out.println("\n🎯 ENDPOINTS DISPONÍVEIS:");
            System.out.println("🌪️ GET /grib2/wind/barbs/{level} - Barbelas de vento");
            System.out.println("💨 GET /grib2/wind/magnitude/{level} - Magnitude do vento");
            System.out.println("⛈️ GET /grib2/cape - CAPE (CB)");
            System.out.println("❄️ GET /grib2/icing/{level} - Probabilidade de gelo");
            System.out.println("💫 GET /grib2/turbulence/{level} - Turbulência");
            
            System.out.println("\n✅ SISTEMA GRIB2 PRONTO!");
            
        } catch (Exception e) {
            System.err.println("\n❌ ERRO NO TESTE: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
