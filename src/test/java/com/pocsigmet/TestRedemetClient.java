package com.pocsigmet;

public class TestRedemetClient {
    public static void main(String[] args) {
        try {
            RedemetSigmetClient client = new RedemetSigmetClient();
            
            System.out.println("🔐 Autenticando...");
            String token = client.authenticate();
            System.out.println("✅ Token obtido: " + token.substring(0, 20) + "...");
            
            System.out.println("📡 Obtendo SIGMETs...");
            String sigmets = client.getSigmets();
            System.out.println("📋 SIGMETs obtidos:");
            System.out.println(sigmets);
            
        } catch (Exception e) {
            System.err.println("❌ Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
