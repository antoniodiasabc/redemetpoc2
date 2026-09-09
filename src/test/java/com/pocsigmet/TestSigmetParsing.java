package com.pocsigmet;

public class TestSigmetParsing {
    public static void main(String[] args) {
        try {
            RedemetSigmetClient client = new RedemetSigmetClient();
            
            System.out.println("🔐 Autenticando...");
            client.authenticate();
            
            System.out.println("📡 Obtendo SIGMETs para SBAZ...");
            String sigmetData = client.getSigmetForFir("SBAZ");
            
            System.out.println("📋 Dados brutos (primeiras 5 linhas):");
            String[] lines = sigmetData.split("\n");
            int count = 0;
            int validCount = 0;
            for (String line : lines) {
                if (line.contains("SIGMET") && line.contains("VALID") && line.contains("WI ")) {
                    count++;
                    if (count <= 2) { // Mostrar apenas os primeiros 2
                        System.out.println("SIGMET " + count + ": " + line.substring(0, Math.min(100, line.length())) + "...");
                        
                        // Verificar se tem coordenadas válidas
                        int wiIndex = line.indexOf("WI ");
                        if (wiIndex != -1) {
                            String coordSection = line.substring(wiIndex + 3);
                            int topIndex = coordSection.indexOf(" TOP ");
                            if (topIndex != -1) {
                                coordSection = coordSection.substring(0, topIndex);
                            }
                            String[] coords = coordSection.split(" - ");
                            System.out.println("  Coordenadas encontradas: " + coords.length);
                            if (coords.length > 0) {
                                System.out.println("  Primeira coord: " + coords[0].trim());
                                if (coords[0].trim().matches("^[NS]\\d{4} [WE]\\d{5}$")) {
                                    validCount++;
                                    System.out.println("  ✅ Formato válido");
                                } else {
                                    System.out.println("  ❌ Formato inválido");
                                }
                            }
                        }
                    }
                }
            }
            System.out.println("Total SIGMETs: " + count + ", Válidos: " + validCount);
            
        } catch (Exception e) {
            System.err.println("❌ Erro: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
