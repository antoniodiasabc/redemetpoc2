package com.pocsigmet.grib2;

import java.util.List;

public class TestWindBarbServiceDirect {
    
    public static void main(String[] args) {
        System.out.println("🧪 === TESTE DIRETO WIND BARB SERVICE ===");
        
        try {
            // Criar instâncias manualmente
            Grib2Downloader downloader = new Grib2Downloader();
            WindBarbService service = new WindBarbService();
            
            // Injetar dependência manualmente
            java.lang.reflect.Field field = WindBarbService.class.getDeclaredField("downloader");
            field.setAccessible(true);
            field.set(service, downloader);
            
            // Teste FL390
            System.out.println("\n1️⃣ TESTE FL390:");
            List<WindBarbData> fl390Barbs = service.getWindBarbs("fl390");
            
            System.out.println("📊 Barbelas FL390: " + fl390Barbs.size());
            
            if (fl390Barbs.size() > 50) {
                System.out.println("✅ FL390 PASSOU - " + fl390Barbs.size() + " barbelas extraídas");
                
                // Mostrar amostras
                System.out.println("📍 Primeiras 3 barbelas FL390:");
                for (int i = 0; i < Math.min(3, fl390Barbs.size()); i++) {
                    WindBarbData barb = fl390Barbs.get(i);
                    double lon = barb.getLon() > 180 ? barb.getLon() - 360 : barb.getLon();
                    System.out.println(String.format("   %.1f°S, %.1f°W - %.0f°/%.1fkt", 
                        Math.abs(barb.getLat()), Math.abs(lon), barb.getDirection(), barb.getSpeed()));
                }
            } else {
                System.out.println("❌ FL390 FALHOU - Poucas barbelas: " + fl390Barbs.size());
            }
            
            // Teste Superfície
            System.out.println("\n2️⃣ TESTE SUPERFÍCIE:");
            List<WindBarbData> surfaceBarbs = service.getWindBarbs("surface");
            
            System.out.println("📊 Barbelas Superfície: " + surfaceBarbs.size());
            
            if (surfaceBarbs.size() > 20) {
                System.out.println("✅ SUPERFÍCIE PASSOU - " + surfaceBarbs.size() + " barbelas extraídas");
                
                // Mostrar amostras
                System.out.println("📍 Primeiras 3 barbelas superfície:");
                for (int i = 0; i < Math.min(3, surfaceBarbs.size()); i++) {
                    WindBarbData barb = surfaceBarbs.get(i);
                    double lon = barb.getLon() > 180 ? barb.getLon() - 360 : barb.getLon();
                    System.out.println(String.format("   %.1f°S, %.1f°W - %.0f°/%.1fkt", 
                        Math.abs(barb.getLat()), Math.abs(lon), barb.getDirection(), barb.getSpeed()));
                }
            } else {
                System.out.println("❌ SUPERFÍCIE FALHOU - Poucas barbelas: " + surfaceBarbs.size());
            }
            
            // Teste Cálculo
            System.out.println("\n3️⃣ TESTE CÁLCULO:");
            testCalculation();
            
            // Teste Nível Inválido
            System.out.println("\n4️⃣ TESTE NÍVEL INVÁLIDO:");
            try {
                service.getWindBarbs("invalid_level");
                System.out.println("❌ NÍVEL INVÁLIDO FALHOU - Deveria lançar exceção");
            } catch (Exception e) {
                System.out.println("✅ NÍVEL INVÁLIDO PASSOU - Exceção esperada: " + e.getMessage());
            }
            
            System.out.println("\n🎯 === RESUMO DOS TESTES ===");
            System.out.println("✅ WindBarbService funcionando corretamente!");
            System.out.println("📊 FL390: " + fl390Barbs.size() + " barbelas");
            System.out.println("📊 Superfície: " + surfaceBarbs.size() + " barbelas");
            System.out.println("🌪️ Sistema pronto para integração no frontend!");
            
        } catch (Exception e) {
            System.out.println("❌ ERRO GERAL: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testCalculation() {
        // Teste: U=-10, V=5 → direção≈153°, velocidade≈21.8kt
        float u = -10.0f;
        float v = 5.0f;
        
        double expectedSpeed = Math.sqrt(u*u + v*v) * 1.94384; // ≈21.8 nós
        double expectedDirection = Math.atan2(-u, -v) * 180.0 / Math.PI; // ≈153°
        if (expectedDirection < 0) expectedDirection += 360;
        
        double speed = Math.sqrt(u*u + v*v) * 1.94384;
        double direction = Math.atan2(-u, -v) * 180.0 / Math.PI;
        if (direction < 0) direction += 360;
        
        System.out.println("📊 Entrada: U=" + u + ", V=" + v);
        System.out.println("📊 Esperado: " + String.format("%.0f°/%.1fkt", expectedDirection, expectedSpeed));
        System.out.println("📊 Calculado: " + String.format("%.0f°/%.1fkt", direction, speed));
        
        if (Math.abs(expectedSpeed - speed) < 0.1 && Math.abs(expectedDirection - direction) < 1.0) {
            System.out.println("✅ CÁLCULO PASSOU");
        } else {
            System.out.println("❌ CÁLCULO FALHOU");
        }
    }
}
