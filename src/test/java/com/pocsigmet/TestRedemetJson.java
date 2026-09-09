package com.pocsigmet;

public class TestRedemetJson {
    public static void main(String[] args) {
        try {
            RedemetSigmetClient client = new RedemetSigmetClient();
            
            System.out.println("🔐 Autenticando...");
            String token = client.authenticate();
            System.out.println("✅ Token obtido: " + token.substring(0, 20) + "...");
            
            System.out.println("📡 Obtendo SIGMETs JSON...");
            String sigmetsJson = client.getSigmetsJson();
            System.out.println("📋 SIGMETs JSON:");
            System.out.println(sigmetsJson);
            
        } catch (Exception e) {
            System.err.println("❌ Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
