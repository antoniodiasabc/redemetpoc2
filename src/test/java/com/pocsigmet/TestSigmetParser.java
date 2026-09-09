package com.pocsigmet;

import java.util.Map;

public class TestSigmetParser {
    public static void main(String[] args) {
        String sigmetLine = "SBBS SIGMET 30 VALID 191637/191930 SBBS - SBBS BRASILIA FIR EMBD TS FCST WI S1724 W05407 - S1925 W05217 - S1926 W05216 - S2020 W05127 - S2151 W05017 - S1953 W05014 - S1844 W04951 - S1708 W04916 - S1422 W05100 - S1240 W05035 - S1242 W05037 - S1238 W05207 - S1153 W05242 - S1153 W05242 - S1155 W05241 - S1155 W05244 - S1211 W05303 - S1258 W05330 - S1434 W05338 - S1643 W05306 - S1724 W05407 TOP FL450 STNR NC=";
        
        try {
            RedemetSigmetClient client = new RedemetSigmetClient();
            
            // Usar reflection para acessar método privado
            java.lang.reflect.Method parseMethod = client.getClass().getDeclaredMethod("parseSigmetCoords", String.class, String.class);
            parseMethod.setAccessible(true);
            
            Map<String, Object> result = (Map<String, Object>) parseMethod.invoke(client, sigmetLine, "SBBS");
            
            if (result != null) {
                System.out.println("✅ SIGMET parseado com sucesso!");
                System.out.println("Tipo: " + result.get("type"));
                
                Map<String, Object> geometry = (Map<String, Object>) result.get("geometry");
                System.out.println("Geometria: " + geometry.get("type"));
                
                java.util.List<java.util.List<java.util.List<Double>>> coords = 
                    (java.util.List<java.util.List<java.util.List<Double>>>) geometry.get("coordinates");
                
                System.out.println("Número de coordenadas: " + coords.get(0).size());
                System.out.println("\n📍 PONTOS DO POLÍGONO:");
                for (int i = 0; i < coords.get(0).size(); i++) {
                    java.util.List<Double> point = coords.get(0).get(i);
                    System.out.println(String.format("%2d: [%8.4f, %7.4f]", i+1, point.get(0), point.get(1)));
                }
                
                Map<String, Object> properties = (Map<String, Object>) result.get("properties");
                System.out.println("\nFIR: " + properties.get("fir"));
                
            } else {
                System.out.println("❌ Falha no parsing do SIGMET");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
