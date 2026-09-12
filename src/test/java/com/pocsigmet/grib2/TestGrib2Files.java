package com.pocsigmet.grib2;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestGrib2Files {
    
    public static void main(String[] args) {
        System.out.println("📁 === PROCURANDO ARQUIVOS GRIB2 MENORES ===");
        
        try {
            // Procurar arquivos GRIB2 no diretório
            String baseUrl = "https://nomads.ncep.noaa.gov/pub/data/nccf/com/gfs/prod/gfs.20260325/12/atmos/";
            
            System.out.println("\n🔍 Procurando arquivos .grib2:");
            listGrib2Files(baseUrl);
            
            // Testar download de arquivo completo menor
            System.out.println("\n🧪 Testando download sem filtros (arquivo menor):");
            testFullDownload();
            
        } catch (Exception e) {
            System.err.println("❌ ERRO: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void listGrib2Files(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains(".grib2") || line.contains(".grb2") || line.contains("pgrb2")) {
                    // Extrair nome e tamanho
                    if (line.contains("href=")) {
                        String[] parts = line.split(">");
                        if (parts.length > 2) {
                            String fileName = parts[1].replace("</a", "");
                            String sizeInfo = parts.length > 3 ? parts[3] : "";
                            System.out.println("📄 " + fileName + " " + sizeInfo);
                        }
                    }
                }
            }
            reader.close();
            
        } catch (Exception e) {
            System.out.println("❌ Erro ao listar GRIB2: " + e.getMessage());
        }
    }
    
    private static void testFullDownload() {
        try {
            // Testar URL sem filtros de variáveis (só região)
            String fullUrl = "https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25.pl" +
                "?file=gfs.t12z.pgrb2.0p25.f000" +
                "&subregion=&leftlon=-85&rightlon=-10&toplat=15&bottomlat=-60" +
                "&dir=%2Fgfs.20260325%2F12%2Fatmos";
            
            System.out.println("🔗 URL sem filtros de variáveis:");
            System.out.println(fullUrl);
            
            // Testar tamanho
            URL url = new URL(fullUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                long contentLength = connection.getContentLengthLong();
                System.out.println("📊 Tamanho estimado: " + (contentLength / 1024 / 1024) + " MB");
            } else {
                System.out.println("❌ Response code: " + responseCode);
            }
            
        } catch (Exception e) {
            System.out.println("❌ Erro no teste: " + e.getMessage());
        }
    }
}
