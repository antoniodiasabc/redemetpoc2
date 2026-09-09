package com.pocsigmet;

public class TestRedemetSimple {
    public static void main(String[] args) {
        try {
            RedemetSigmetClient client = new RedemetSigmetClient();
            System.out.println("🔐 Autenticando...");
            String token = client.authenticate();
            System.out.println("✅ Token obtido: " + token.substring(0, 20) + "...");
            
            // Testar com METAR primeiro como no exemplo
            String metarData = client.getMetar("SBSP");
            System.out.println("📋 METAR SBSP:");
            System.out.println(metarData);
            
        } catch (Exception e) {
            System.err.println("❌ Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
