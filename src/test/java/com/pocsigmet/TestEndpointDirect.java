package com.pocsigmet;

public class TestEndpointDirect {
    public static void main(String[] args) {
        try {
            System.out.println("🔍 Testando endpoint diretamente...");
            
            RedemetSigmetClient client = new RedemetSigmetClient();
            System.out.println("🔍 Cliente criado");
            
            String json = client.getSigmetsJson();
            System.out.println("🔍 JSON obtido, tamanho: " + json.length());
            
            if (json.length() > 0) {
                System.out.println("🔍 Primeiros 500 chars:");
                System.out.println(json.substring(0, Math.min(500, json.length())));
            }
            
        } catch (Exception e) {
            System.err.println("❌ Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
