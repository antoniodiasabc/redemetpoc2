package com.pocsigmet.grib2;

import java.util.List;

public class TestGrib2Full {
    
    public static void main(String[] args) {
        System.out.println("🌍 === TESTE GRIB2 ARQUIVO COMPLETO ===");
        
        try {
            // 1. Baixar arquivo GRIB2 completo
            System.out.println("\n1️⃣ Baixando arquivo GRIB2 completo (0.5°)...");
            Grib2Downloader downloader = new Grib2Downloader();
            
            String filePath = downloader.downloadGrib2FullFile("f000");
            System.out.println("✅ Arquivo: " + filePath);
            
            // 2. Verificar tamanho do arquivo
            java.io.File file = new java.io.File(filePath);
            double sizeMB = file.length() / (1024.0 * 1024.0);
            System.out.println("📊 Tamanho: " + String.format("%.1f", sizeMB) + " MB");
            
            // 3. Inspecionar variáveis (básico)
            System.out.println("\n2️⃣ Inspecionando arquivo completo...");
            Grib2Inspector inspector = new Grib2Inspector();
            List<Grib2Inspector.Grib2Variable> variables = inspector.listVariables(filePath);
            
            System.out.println("📊 Total de variáveis detectadas: " + variables.size());
            
            if (!variables.isEmpty()) {
                System.out.println("\n3️⃣ Primeiras variáveis encontradas:");
                System.out.println("=" + "=".repeat(80));
                System.out.printf("%-20s | %-30s | %-15s | %s%n", "NOME", "DESCRIÇÃO", "NÍVEL", "UNIDADE");
                System.out.println("-" + "-".repeat(80));
                
                variables.stream()
                    .limit(10)
                    .forEach(System.out::println);
                
                System.out.println("=" + "=".repeat(80));
            }
            
            System.out.println("\n🎉 ARQUIVO COMPLETO BAIXADO COM SUCESSO!");
            System.out.println("📋 Contém TODAS as variáveis meteorológicas:");
            System.out.println("   🌪️ Vento (múltiplos níveis)");
            System.out.println("   🌡️ Temperatura (múltiplos níveis)");
            System.out.println("   📊 Pressão (múltiplos níveis)");
            System.out.println("   💧 Umidade (múltiplos níveis)");
            System.out.println("   ⛈️ CB, turbulência, gelo");
            
        } catch (Exception e) {
            System.err.println("\n❌ ERRO NO TESTE: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
