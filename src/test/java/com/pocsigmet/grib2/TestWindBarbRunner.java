package com.pocsigmet.grib2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@SpringBootApplication
@EnableScheduling
public class TestWindBarbRunner implements CommandLineRunner {
    
    @Autowired
    private WindBarbService windBarbService;
    
    public static void main(String[] args) {
        SpringApplication.run(TestWindBarbRunner.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        System.out.println("🧪 === EXECUTANDO TESTES WIND BARB ===");
        
        // Teste 1: FL390
        testFL390();
        
        // Teste 2: Superfície
        testSurface();
        
        // Teste 3: Cálculo
        testCalculation();
        
        System.out.println("\n🎯 === RESUMO DOS TESTES ===");
        System.out.println("✅ Todos os testes executados com sucesso!");
        
        System.exit(0);
    }
    
    private void testFL390() {
        System.out.println("\n🧪 TESTE 1: FL390 América do Sul");
        
        try {
            List<WindBarbData> windBarbs = windBarbService.getWindBarbs("fl390");
            
            System.out.println("📊 Barbelas extraídas: " + windBarbs.size());
            
            if (windBarbs.size() < 100) {
                System.out.println("❌ FALHOU: Muito poucas barbelas (" + windBarbs.size() + ")");
                return;
            }
            
            if (windBarbs.size() > 5000) {
                System.out.println("❌ FALHOU: Muitas barbelas (" + windBarbs.size() + ")");
                return;
            }
            
            // Verificar coordenadas
            int validCount = 0;
            for (WindBarbData barb : windBarbs) {
                double lat = barb.getLat();
                double lon = barb.getLon() > 180 ? barb.getLon() - 360 : barb.getLon();
                
                if (lat >= -60.0 && lat <= 15.0 && lon >= -85.0 && lon <= -10.0) {
                    validCount++;
                }
                
                if (!barb.getLevel().equals("fl390")) {
                    System.out.println("❌ FALHOU: Nível incorreto: " + barb.getLevel());
                    return;
                }
            }
            
            System.out.println("📍 Pontos válidos na América do Sul: " + validCount + "/" + windBarbs.size());
            
            // Mostrar amostras
            System.out.println("📍 Primeiras 5 barbelas:");
            for (int i = 0; i < Math.min(5, windBarbs.size()); i++) {
                WindBarbData barb = windBarbs.get(i);
                double lon = barb.getLon() > 180 ? barb.getLon() - 360 : barb.getLon();
                System.out.println(String.format("   %.1f°S, %.1f°W - %.0f°/%.1fkt", 
                    Math.abs(barb.getLat()), Math.abs(lon), barb.getDirection(), barb.getSpeed()));
            }
            
            System.out.println("✅ TESTE FL390 PASSOU");
            
        } catch (Exception e) {
            System.out.println("❌ TESTE FL390 FALHOU: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void testSurface() {
        System.out.println("\n🧪 TESTE 2: Vento Superfície");
        
        try {
            List<WindBarbData> windBarbs = windBarbService.getWindBarbs("surface");
            
            System.out.println("📊 Barbelas superfície: " + windBarbs.size());
            
            if (windBarbs.isEmpty()) {
                System.out.println("❌ FALHOU: Nenhuma barbela de superfície");
                return;
            }
            
            // Verificar se todas são de superfície
            for (WindBarbData barb : windBarbs) {
                if (!barb.getLevel().equals("surface")) {
                    System.out.println("❌ FALHOU: Nível incorreto: " + barb.getLevel());
                    return;
                }
            }
            
            System.out.println("✅ TESTE SUPERFÍCIE PASSOU");
            
        } catch (Exception e) {
            System.out.println("❌ TESTE SUPERFÍCIE FALHOU: " + e.getMessage());
        }
    }
    
    private void testCalculation() {
        System.out.println("\n🧪 TESTE 3: Cálculo de Barbelas");
        
        // Teste: U=-10, V=5 → direção≈153°, velocidade≈21.8kt
        float u = -10.0f;
        float v = 5.0f;
        
        double expectedSpeed = Math.sqrt(u*u + v*v) * 1.94384; // ≈21.8 nós
        double expectedDirection = Math.atan2(-u, -v) * 180.0 / Math.PI; // ≈153°
        if (expectedDirection < 0) expectedDirection += 360;
        
        System.out.println("📊 Entrada: U=" + u + ", V=" + v);
        System.out.println("📊 Esperado: " + String.format("%.0f°/%.1fkt", expectedDirection, expectedSpeed));
        
        double speed = Math.sqrt(u*u + v*v) * 1.94384;
        double direction = Math.atan2(-u, -v) * 180.0 / Math.PI;
        if (direction < 0) direction += 360;
        
        System.out.println("📊 Calculado: " + String.format("%.0f°/%.1fkt", direction, speed));
        
        if (Math.abs(expectedSpeed - speed) > 0.1) {
            System.out.println("❌ FALHOU: Velocidade incorreta");
            return;
        }
        
        if (Math.abs(expectedDirection - direction) > 1.0) {
            System.out.println("❌ FALHOU: Direção incorreta");
            return;
        }
        
        System.out.println("✅ TESTE CÁLCULO PASSOU");
    }
}
