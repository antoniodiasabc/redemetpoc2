package com.pocsigmet;

import java.io.IOException;

public class TestAvisoAerodromo {
    
    public static void main(String[] args) {
        try {
            System.out.println("🔍 TESTE UNITÁRIO - AVISO AERÓDROMO");
            System.out.println("=====================================");
            
            RedemetMetarClient client = new RedemetMetarClient();
            
            // Teste 1: Buscar avisos de aeródromo
            System.out.println("\n1️⃣ Buscando AVISO_AERODROMO...");
            String avisoAerodromo = client.getAvisoAerodromo();
            System.out.println("📄 Dados brutos AVISO_AERODROMO:");
            if (avisoAerodromo.isEmpty()) {
                System.out.println("❌ Nenhum aviso encontrado");
            } else {
                String[] avisos = avisoAerodromo.split("=");
                System.out.println("📊 Total de avisos: " + avisos.length);
                for (int i = 0; i < avisos.length; i++) {
                    System.out.println("📋 Aviso " + (i+1) + ": " + avisos[i].trim());
                }
            }
            
            // Teste 2: Buscar avisos de cortante de vento
            System.out.println("\n2️⃣ Buscando AVISO_CORTANTE_VENTO...");
            String avisoCortante = client.getAvisoCortanteVento();
            System.out.println("📄 Dados brutos AVISO_CORTANTE_VENTO:");
            if (avisoCortante.isEmpty()) {
                System.out.println("❌ Nenhum aviso encontrado");
            } else {
                String[] avisos = avisoCortante.split("=");
                System.out.println("📊 Total de avisos: " + avisos.length);
                for (int i = 0; i < avisos.length; i++) {
                    System.out.println("📋 Aviso " + (i+1) + ": " + avisos[i].trim());
                }
            }
            
            // Teste 3: Verificar aeroportos específicos
            System.out.println("\n3️⃣ Testando aeroportos específicos...");
            String[] testAirports = {"SBAN", "SBBR", "SBGO", "SBNV", "SNZR"};
            
            for (String icao : testAirports) {
                boolean hasAviso = client.hasAvisoForAirport(icao);
                System.out.println("🛩️ " + icao + ": " + (hasAviso ? "🔴 TEM AVISO" : "✅ SEM AVISO"));
            }
            
            // Teste 4: Verificar padrão específico
            System.out.println("\n4️⃣ Procurando padrão específico...");
            String allAvisos = avisoAerodromo + (avisoCortante.isEmpty() ? "" : "=" + avisoCortante);
            if (allAvisos.contains("AD WRNG") && allAvisos.contains("VALID 201540/201940")) {
                System.out.println("✅ Padrão encontrado: AD WRNG VALID 201540/201940");
                
                String[] avisos = allAvisos.split("=");
                for (String aviso : avisos) {
                    if (aviso.contains("AD WRNG") && aviso.contains("VALID 201540/201940")) {
                        System.out.println("📋 Aviso completo: " + aviso.trim());
                        
                        // Verificar quais aeroportos estão mencionados
                        for (String icao : testAirports) {
                            if (aviso.contains(icao)) {
                                System.out.println("   🎯 Inclui aeroporto: " + icao);
                            }
                        }
                    }
                }
            } else {
                System.out.println("❌ Padrão não encontrado");
            }
            
            System.out.println("\n✅ TESTE CONCLUÍDO");
            
        } catch (Exception e) {
            System.err.println("❌ Erro no teste: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
