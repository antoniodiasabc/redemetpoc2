package com.pocsigmet;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class TestAvisoHistorico {
    
    public static void main(String[] args) {
        try {
            System.out.println("🔍 TESTE HISTÓRICO - AVISO AERÓDROMO");
            System.out.println("====================================");
            
            RedemetMetarClient client = new RedemetMetarClient();
            client.authenticate();
            
            // Testar com data específica do exemplo: 201540/201940
            LocalDateTime testDate = LocalDateTime.of(2026, 3, 20, 15, 0); // 20/03/2026 15:00
            String dataIni = "2026032015";
            String dataFim = "2026032020";
            
            System.out.println("📅 Testando período: " + dataIni + " até " + dataFim);
            
            // Teste direto da API
            String endpoint = String.format("?local=FIR&msg=AVISO_AERODROMO&data_ini=%s&data_fim=%s&data_hora=nao", 
                                           dataIni, dataFim);
            
            System.out.println("🌐 URL: https://opmet.decea.mil.br/redemet/consulta_redemet" + endpoint);
            
            // Usar método customizado para teste
            String avisoData = testAvisoCustom(client, dataIni, dataFim);
            
            if (avisoData.isEmpty()) {
                System.out.println("❌ Nenhum aviso encontrado no período");
                
                // Testar período mais amplo
                System.out.println("\n🔍 Testando período mais amplo...");
                dataIni = "2026032000";
                dataFim = "2026032023";
                avisoData = testAvisoCustom(client, dataIni, dataFim);
            }
            
            if (!avisoData.isEmpty()) {
                System.out.println("✅ Avisos encontrados!");
                String[] avisos = avisoData.split("=");
                for (int i = 0; i < avisos.length; i++) {
                    System.out.println("📋 Aviso " + (i+1) + ": " + avisos[i].trim());
                    
                    // Verificar se contém o padrão esperado
                    if (avisos[i].contains("AD WRNG") && avisos[i].contains("SBBR")) {
                        System.out.println("   🎯 ENCONTRADO AVISO PARA SBBR!");
                    }
                }
            } else {
                System.out.println("❌ Nenhum aviso encontrado mesmo no período amplo");
                System.out.println("💡 Possíveis causas:");
                System.out.println("   - Avisos não estão ativos no momento");
                System.out.println("   - Formato da mensagem diferente");
                System.out.println("   - Parâmetros da API incorretos");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Erro no teste: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String testAvisoCustom(RedemetMetarClient client, String dataIni, String dataFim) throws IOException, InterruptedException {
        System.out.println("🔍 Buscando avisos de " + dataIni + " até " + dataFim);
        
        String endpoint = String.format("?local=FIR&msg=AVISO_AERODROMO&data_ini=%s&data_fim=%s&data_hora=nao", 
                                       dataIni, dataFim);
        
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://opmet.decea.mil.br/redemet/consulta_redemet" + endpoint))
            .header("Authorization", "Bearer " + getToken(client))
            .header("Accept", "text/plain")
            .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
            .timeout(java.time.Duration.ofSeconds(10))
            .build();
            
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        
        System.out.println("📊 Status: " + response.statusCode());
        String body = response.statusCode() == 200 ? response.body() : "";
        System.out.println("📄 Resposta (primeiros 500 chars): " + 
                         (body.length() > 500 ? body.substring(0, 500) + "..." : body));
        
        return body;
    }
    
    private static String getToken(RedemetMetarClient client) throws IOException, InterruptedException {
        // Usar reflexão para acessar o token privado
        try {
            java.lang.reflect.Field tokenField = client.getClass().getDeclaredField("token");
            tokenField.setAccessible(true);
            return (String) tokenField.get(client);
        } catch (Exception e) {
            // Se não conseguir, autentica novamente
            return client.authenticate();
        }
    }
}
