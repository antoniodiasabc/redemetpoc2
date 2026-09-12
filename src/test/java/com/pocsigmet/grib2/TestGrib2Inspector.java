package com.pocsigmet.grib2;

import java.util.List;

public class TestGrib2Inspector {
    
    public static void main(String[] args) {
        System.out.println("🔍 === TESTE INSPETOR GRIB2 ===");
        
        try {
            // 1. Baixar arquivo GRIB2
            System.out.println("\n1️⃣ Baixando arquivo GRIB2...");
            Grib2Downloader downloader = new Grib2Downloader();
            String filePath = downloader.downloadGrib2File("UGRD", "850_mb");
            System.out.println("✅ Arquivo: " + filePath);
            
            // 2. Inspecionar variáveis
            System.out.println("\n2️⃣ Inspecionando variáveis...");
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
            System.out.println("\n✅ INSPEÇÃO CONCLUÍDA COM SUCESSO!");
            
        } catch (Exception e) {
            System.err.println("\n❌ ERRO NA INSPEÇÃO: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
