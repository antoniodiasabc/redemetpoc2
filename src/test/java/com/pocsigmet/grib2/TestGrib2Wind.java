package com.pocsigmet.grib2;

import java.util.List;

public class TestGrib2Wind {
    
    public static void main(String[] args) {
        System.out.println("🌪️ === TESTE GRIB2 VENTO COMPLETO (UGRD + VGRD) ===");
        
        try {
            // 1. Baixar UGRD + VGRD juntos
            System.out.println("\n1️⃣ Baixando componentes U e V do vento...");
            Grib2Downloader downloader = new Grib2Downloader();
            
            String[] windVariables = {"UGRD", "VGRD"};
            String filePath = downloader.downloadGrib2File(windVariables, "850_mb");
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
            
            // 4. Verificar se temos ambas componentes
            boolean hasUGRD = variables.stream().anyMatch(v -> v.name.equals("UGRD"));
            boolean hasVGRD = variables.stream().anyMatch(v -> v.name.equals("VGRD"));
            
            System.out.println("\n4️⃣ Verificação para barbelas:");
            System.out.println("UGRD (componente U): " + (hasUGRD ? "✅ Presente" : "❌ Ausente"));
            System.out.println("VGRD (componente V): " + (hasVGRD ? "✅ Presente" : "❌ Ausente"));
            
            if (hasUGRD && hasVGRD) {
                System.out.println("🎉 PRONTO PARA GERAR BARBELAS DE VENTO!");
            } else {
                System.out.println("⚠️ Faltam componentes para barbelas completas");
            }
            
            System.out.println("\n✅ TESTE CONCLUÍDO!");
            
        } catch (Exception e) {
            System.err.println("\n❌ ERRO NO TESTE: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
