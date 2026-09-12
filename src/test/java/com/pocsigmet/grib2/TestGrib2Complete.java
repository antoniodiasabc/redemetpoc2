package com.pocsigmet.grib2;

import java.util.List;

public class TestGrib2Complete {
    
    public static void main(String[] args) {
        System.out.println("🌍 === TESTE GRIB2 COMPLETO (MÚLTIPLAS VARIÁVEIS) ===");
        
        try {
            // 1. Baixar múltiplas variáveis meteorológicas
            System.out.println("\n1️⃣ Baixando variáveis meteorológicas completas...");
            Grib2Downloader downloader = new Grib2Downloader();
            
            String[] allVariables = {
                "UGRD",   // Vento U
                "VGRD",   // Vento V  
                "TMP",    // Temperatura
                "HGT",    // Altura geopotencial
                "RH",     // Umidade relativa
                "PRMSL"   // Pressão nível do mar
            };
            
            String filePath = downloader.downloadGrib2File(allVariables, "850_mb");
            System.out.println("✅ Arquivo: " + filePath);
            
            // 2. Inspecionar todas as variáveis
            System.out.println("\n2️⃣ Inspecionando todas as variáveis...");
            Grib2Inspector inspector = new Grib2Inspector();
            List<Grib2Inspector.Grib2Variable> variables = inspector.listVariables(filePath);
            
            // 3. Mostrar resultados
            System.out.println("\n3️⃣ Variáveis encontradas:");
            System.out.println("=" + "=".repeat(80));
            System.out.printf("%-20s | %-30s | %-15s | %s%n", "NOME", "DESCRIÇÃO", "NÍVEL", "UNIDADE");
            System.out.println("-" + "-".repeat(80));
            
            for (Grib2Inspector.Grib2Variable var : variables) {
                System.out.println(var);
            }
            
            System.out.println("=" + "=".repeat(80));
            
            // 4. Verificar disponibilidade
            System.out.println("\n4️⃣ Verificação de variáveis:");
            String[] required = {"UGRD", "VGRD", "TMP", "HGT", "RH", "PRMSL"};
            
            for (String req : required) {
                boolean found = variables.stream().anyMatch(v -> v.name.equals(req));
                System.out.println(req + ": " + (found ? "✅ Presente" : "❌ Ausente"));
            }
            
            System.out.println("\n✅ TESTE COMPLETO CONCLUÍDO!");
            
        } catch (Exception e) {
            System.err.println("\n❌ ERRO NO TESTE: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
