package com.pocsigmet;

public class TestSBGRMetarTaf {
    
    public static void main(String[] args) {
        System.out.println("🛩️ === TESTE METAR + TAF SBGR ===");
        
        try {
            // 1. Testar METAR
            System.out.println("\n1️⃣ Testando METAR para SBGR...");
            RedemetMetarClient metarClient = new RedemetMetarClient();
            String metar = metarClient.getLatestMetar("SBGR");
            
            System.out.println("📊 METAR SBGR:");
            System.out.println("=" + "=".repeat(50));
            System.out.println(metar);
            System.out.println("=" + "=".repeat(50));
            
            // 2. Testar TAF
            System.out.println("\n2️⃣ Testando TAF para SBGR...");
            RedemetSigmetClient sigmetClient = new RedemetSigmetClient();
            String taf = sigmetClient.getTafForAirport("SBGR");
            
            System.out.println("🔮 TAF SBGR:");
            System.out.println("=" + "=".repeat(50));
            if (taf != null && !taf.trim().isEmpty()) {
                System.out.println(taf);
            } else {
                System.out.println("TAF não disponível ou vazio");
            }
            System.out.println("=" + "=".repeat(50));
            
            // 3. Testar endpoint completo
            System.out.println("\n3️⃣ Testando endpoint completo...");
            System.out.println("Acesse: http://localhost:8082/metar_top20_sb");
            System.out.println("Procure por SBGR no JSON de resposta");
            
            // 4. Resumo
            System.out.println("\n📋 RESUMO:");
            System.out.println("METAR: " + (metar.contains("METAR") || metar.contains("SPECI") ? "✅ OK" : "❌ Erro"));
            System.out.println("TAF: " + (taf != null && !taf.trim().isEmpty() ? "✅ OK" : "❌ Não disponível"));
            
        } catch (Exception e) {
            System.err.println("❌ Erro no teste: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
