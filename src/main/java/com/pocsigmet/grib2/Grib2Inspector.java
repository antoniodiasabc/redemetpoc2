package com.pocsigmet.grib2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Grib2Inspector {
    private static final Logger log = LoggerFactory.getLogger(Grib2Inspector.class);
    
    public static class Grib2Variable {
        public String name;
        public String description;
        public String level;
        public String units;
        
        public Grib2Variable(String name, String description, String level, String units) {
            this.name = name;
            this.description = description;
            this.level = level;
            this.units = units;
        }
        
        @Override
        public String toString() {
            return String.format("%-20s | %-30s | %-15s | %s", name, description, level, units);
        }
    }
    
    public List<Grib2Variable> listVariables(String gribFilePath) throws IOException {
        List<Grib2Variable> variables = new ArrayList<>();
        
        log.info(String.valueOf("🔍 Inspecionando arquivo GRIB2: " + gribFilePath));
        
        // Usar wgrib2 se disponível, senão análise básica
        if (isWgrib2Available()) {
            variables = listVariablesWithWgrib2(gribFilePath);
        } else {
            variables = listVariablesBasic(gribFilePath);
        }
        
        log.info(String.valueOf("📊 Total de variáveis encontradas: " + variables.size()));
        return variables;
    }
    
    private boolean isWgrib2Available() {
        try {
            Process process = Runtime.getRuntime().exec("wgrib2 -version");
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private List<Grib2Variable> listVariablesWithWgrib2(String gribFilePath) throws IOException {
        List<Grib2Variable> variables = new ArrayList<>();
        
        try {
            String[] command = {"wgrib2", "-s", gribFilePath};
            Process process = Runtime.getRuntime().exec(command);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                // Formato: 1:0:d=2026032512:UGRD:850 mb:anl:
                String[] parts = line.split(":");
                if (parts.length >= 5) {
                    String name = parts[3];
                    String level = parts[4];
                    String description = getVariableDescription(name);
                    String units = getVariableUnits(name);
                    
                    variables.add(new Grib2Variable(name, description, level, units));
                }
            }
            
            process.waitFor();
            reader.close();
            
        } catch (Exception e) {
            throw new IOException("Erro ao executar wgrib2: " + e.getMessage());
        }
        
        return variables;
    }
    
    private List<Grib2Variable> listVariablesBasic(String gribFilePath) throws IOException {
        List<Grib2Variable> variables = new ArrayList<>();
        
        // Análise baseada no nome do arquivo
        String fileName = new java.io.File(gribFilePath).getName();
        log.info(String.valueOf("📁 Analisando arquivo: " + fileName));
        
        // Detectar variáveis pelo nome do arquivo
        if (fileName.contains("UGRD")) {
            variables.add(new Grib2Variable("UGRD", "U-Component of Wind", "850 mb", "m/s"));
        }
        if (fileName.contains("VGRD")) {
            variables.add(new Grib2Variable("VGRD", "V-Component of Wind", "850 mb", "m/s"));
        }
        if (fileName.contains("TMP")) {
            variables.add(new Grib2Variable("TMP", "Temperature", "850 mb", "K"));
        }
        if (fileName.contains("HGT")) {
            variables.add(new Grib2Variable("HGT", "Geopotential Height", "850 mb", "gpm"));
        }
        if (fileName.contains("RH")) {
            variables.add(new Grib2Variable("RH", "Relative Humidity", "850 mb", "%"));
        }
        if (fileName.contains("PRMSL")) {
            variables.add(new Grib2Variable("PRMSL", "Pressure reduced to MSL", "sea level", "Pa"));
        }
        
        // Verificar tamanho do arquivo
        java.io.File file = new java.io.File(gribFilePath);
        log.info("📊 Tamanho do arquivo: " + file.length() + " bytes");
        
        return variables;
    }
    
    private String getVariableDescription(String name) {
        switch (name) {
            case "UGRD": return "U-Component of Wind";
            case "VGRD": return "V-Component of Wind";
            case "TMP": return "Temperature";
            case "HGT": return "Geopotential Height";
            case "RH": return "Relative Humidity";
            case "PRMSL": return "Pressure reduced to MSL";
            case "PWAT": return "Precipitable Water";
            case "CAPE": return "Convective Available Potential Energy";
            case "CIN": return "Convective Inhibition";
            default: return "Unknown Variable";
        }
    }
    
    private String getVariableUnits(String name) {
        switch (name) {
            case "UGRD":
            case "VGRD": return "m/s";
            case "TMP": return "K";
            case "HGT": return "gpm";
            case "RH": return "%";
            case "PRMSL": return "Pa";
            case "PWAT": return "kg/m²";
            case "CAPE":
            case "CIN": return "J/kg";
            default: return "unknown";
        }
    }
}
