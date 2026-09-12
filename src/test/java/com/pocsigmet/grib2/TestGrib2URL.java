package com.pocsigmet.grib2;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestGrib2URL {
    
    public static void main(String[] args) {
        System.out.println("🔗 === TESTE URL GRIB2 E LISTAGEM ===");
        
        try {
            // 1. Mostrar URL que está sendo usada
            Grib2Downloader downloader = new Grib2Downloader();
            
            // Simular construção da URL
            String run = "2026032512"; // Run atual
            String forecast = "f000";
            String[] variables = {"UGRD", "VGRD", "TMP", "HGT", "RH", "PRMSL"};
            String level = "850_mb";
            
            String url = buildTestUrl(run, forecast, variables, level);
            System.out.println("\n1️⃣ URL sendo usada:");
            System.out.println(url);
            
            // 2. Tentar listar arquivos disponíveis no diretório
            String dirUrl = "https://nomads.ncep.noaa.gov/dods/gfs_0p25/gfs20260325/gfs_0p25_12z";
            System.out.println("\n2️⃣ Tentando listar diretório:");
            System.out.println(dirUrl);
            
            listDirectory(dirUrl);
            
            // 3. URL alternativa - FTP
            String ftpUrl = "https://nomads.ncep.noaa.gov/pub/data/nccf/com/gfs/prod/gfs.20260325/12/atmos/";
            System.out.println("\n3️⃣ URL FTP alternativa:");
            System.out.println(ftpUrl);
            
            listDirectory(ftpUrl);
            
        } catch (Exception e) {
            System.err.println("❌ ERRO: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String buildTestUrl(String run, String forecast, String[] variables, String level) {
        String date = run.substring(0, 8);
        String hour = run.substring(8, 10);
        
        StringBuilder url = new StringBuilder("https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25.pl");
        url.append("?file=gfs.t").append(hour).append("z.pgrb2.0p25.").append(forecast);
        url.append("&lev_").append(level).append("=on");
        
        for (String variable : variables) {
            url.append("&var_").append(variable).append("=on");
        }
        
        url.append("&subregion=&leftlon=-85&rightlon=-10&toplat=15&bottomlat=-60");
        url.append("&dir=%2Fgfs.").append(date).append("%2F").append(hour).append("%2Fatmos");
        
        return url.toString();
    }
    
    private static void listDirectory(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                int count = 0;
                
                while ((line = reader.readLine()) != null && count < 20) {
                    if (line.contains(".grib2") || line.contains("gfs.t")) {
                        System.out.println("📁 " + line.trim());
                        count++;
                    }
                }
                reader.close();
            } else {
                System.out.println("❌ Não foi possível acessar: " + responseCode);
            }
            
        } catch (Exception e) {
            System.out.println("❌ Erro ao listar: " + e.getMessage());
        }
    }
}
