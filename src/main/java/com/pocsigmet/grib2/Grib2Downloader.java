package com.pocsigmet.grib2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@org.springframework.stereotype.Component
public class Grib2Downloader {
    private static final Logger log = LoggerFactory.getLogger(Grib2Downloader.class);
    
    private static final String GRIB2_DIR = "data/grib2/";
    private static final String GFS_BASE_URL = "https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25.pl";
    
    public Grib2Downloader() {
        createDirectories();
    }
    
    private void createDirectories() {
        try {
            Files.createDirectories(Paths.get(GRIB2_DIR));
            log.info(String.valueOf("📁 Diretório GRIB2 criado: " + GRIB2_DIR));
        } catch (IOException e) {
            log.warn(String.valueOf("❌ Erro ao criar diretório GRIB2: " + e.getMessage()));
        }
    }
    
    public String downloadGrib2FullFile(String forecast) throws IOException {
        String run = getLatestGFSRun();
        
        String fileName = String.format("gfs_%s_pgrb2full_0p50_%s.grib2", run, forecast);
        Path filePath = Paths.get(GRIB2_DIR, fileName);
        
        // Se arquivo já existe e é recente (< 3 horas), usar cache
        // Mas só se não houver download em andamento (.tmp)
        Path tmpPath = Paths.get(GRIB2_DIR, fileName + ".gbx9.tmp");
        boolean downloading = Files.exists(tmpPath);
        // Se .tmp existe mas o .grib2 já está completo, o .tmp é órfão — deletar
        if (downloading && Files.exists(filePath) && filePath.toFile().length() > 100_000_000L) {
            try { Files.delete(tmpPath); } catch (Exception ignored) {}
            downloading = false;
        }
        if (Files.exists(filePath) && isFileRecent(filePath, 3) && !downloading) {
            log.info(String.valueOf("📋 Usando cache GRIB2 FULL: " + fileName));
            return filePath.toString();
        }
        // Se download em andamento, tentar usar arquivo anterior
        if (downloading) {
            log.info("⏳ Download em andamento para " + fileName + ", buscando arquivo anterior...");
            java.util.Optional<Path> prev = Files.list(Paths.get(GRIB2_DIR))
                .filter(p -> p.getFileName().toString().matches("gfs_.*_pgrb2full_0p50_f000\\.grib2") && !p.equals(filePath))
                .filter(p -> !Files.exists(Paths.get(p + ".gbx9.tmp")))
                .filter(p -> p.toFile().length() > 100_000_000L)
                .max(java.util.Comparator.comparing(p -> p.getFileName().toString()));
            if (prev.isPresent()) {
                log.info(String.valueOf("📋 Usando arquivo anterior: " + prev.get().getFileName()));
                return prev.get().toString();
            }
        }
        
        String url = buildGFSFullUrl(run, forecast);
        log.info(String.valueOf("⬇️ Baixando GRIB2 FULL: " + url));
        
        downloadFile(url, filePath.toString());
        
        log.info(String.valueOf("✅ GRIB2 FULL baixado: " + fileName));
        return filePath.toString();
    }
    
    public String downloadGrib2File(String[] variables, String level) throws IOException {
        String run = getLatestGFSRun();
        String forecast = "f000"; // Análise atual
        
        String varsString = String.join("_", variables);
        String fileName = String.format("gfs_%s_%s_%s_%s.grib2", run, forecast, varsString, level);
        Path filePath = Paths.get(GRIB2_DIR, fileName);
        
        // Se arquivo já existe e é recente (< 3 horas), usar cache
        if (Files.exists(filePath) && isFileRecent(filePath, 3)) {
            log.info(String.valueOf("📋 Usando cache GRIB2: " + fileName));
            return filePath.toString();
        }
        
        String url = buildGFSUrl(run, forecast, variables, level);
        log.info(String.valueOf("⬇️ Baixando GRIB2: " + url));
        
        downloadFile(url, filePath.toString());
        
        log.info(String.valueOf("✅ GRIB2 baixado: " + fileName));
        return filePath.toString();
    }
    
    // Método compatível com versão anterior
    public String downloadGrib2File(String variable, String level) throws IOException {
        return downloadGrib2File(new String[]{variable}, level);
    }
    
    private String buildGFSFullUrl(String run, String forecast) {
        String date = run.substring(0, 8);
        String hour = run.substring(8, 10);
        
        // URL direta para arquivo completo
        String url = String.format("https://nomads.ncep.noaa.gov/pub/data/nccf/com/gfs/prod/gfs.%s/%s/atmos/gfs.t%sz.pgrb2full.0p50.%s",
                                 date, hour, hour, forecast);
        
        return url;
    }
    
    public String getLatestGFSRun() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // GFS roda a cada 6h: 00, 06, 12, 18 UTC
        // Arquivo disponível ~4h após o horário do run
        int hour = now.getHour();
        int latestRun = (hour / 6) * 6;

        // Se menos de 4h desde o run atual, usa o anterior
        if ((hour - latestRun) < 4) {
            latestRun -= 6;
            if (latestRun < 0) {
                latestRun = 18;
                now = now.minusDays(1);
            }
        }

        LocalDateTime runTime = now.withHour(latestRun).withMinute(0).withSecond(0);
        return runTime.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
    }
    
    private String buildGFSUrl(String run, String forecast, String[] variables, String level) {
        String date = run.substring(0, 8);
        String hour = run.substring(8, 10);
        
        StringBuilder url = new StringBuilder(GFS_BASE_URL);
        url.append("?file=gfs.t").append(hour).append("z.pgrb2.0p25.").append(forecast);
        url.append("&lev_").append(level).append("=on");
        
        // Adicionar múltiplas variáveis
        for (String variable : variables) {
            url.append("&var_").append(variable).append("=on");
        }
        
        url.append("&subregion=&leftlon=-85&rightlon=-10&toplat=15&bottomlat=-60");
        url.append("&dir=%2Fgfs.").append(date).append("%2F").append(hour).append("%2Fatmos");
        
        return url.toString();
    }
    
    private void downloadFile(String urlString, String filePath) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        
        try (InputStream in = connection.getInputStream();
             FileOutputStream out = new FileOutputStream(filePath)) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            log.info("📊 Arquivo baixado: " + totalBytes + " bytes");
        }
    }
    
    private boolean isFileRecent(Path filePath, int maxHours) {
        try {
            long fileTime = Files.getLastModifiedTime(filePath).toMillis();
            long currentTime = System.currentTimeMillis();
            long maxAge = maxHours * 60 * 60 * 1000L;
            
            return (currentTime - fileTime) < maxAge;
        } catch (IOException e) {
            return false;
        }
    }
}
