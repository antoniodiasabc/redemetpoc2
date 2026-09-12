package com.pocsigmet.grib2;

import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.Variable;
import ucar.ma2.Array;
import ucar.ma2.Index;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@org.springframework.stereotype.Component
public class Grib2DataExtractor {
    
    public static class WindBarb {
        public double lat, lon, u, v, speed, direction;
        
        public WindBarb(double lat, double lon, double u, double v) {
            this.lat = lat;
            this.lon = lon;
            this.u = u;
            this.v = v;
            this.speed = Math.sqrt(u*u + v*v) * 1.94384; // m/s para nós
            this.direction = Math.atan2(-u, -v) * 180.0 / Math.PI;
            if (this.direction < 0) this.direction += 360;
        }
    }
    
    public static class GridData {
        public double lat, lon, value;
        public String variable, level, units;
        
        public GridData(double lat, double lon, double value, String variable, String level, String units) {
            this.lat = lat;
            this.lon = lon;
            this.value = value;
            this.variable = variable;
            this.level = level;
            this.units = units;
        }
    }
    
    public List<WindBarb> extractWindBarbs(String gribFilePath, String level) throws IOException {
        List<WindBarb> windBarbs = new ArrayList<>();
        
        try (NetcdfFile ncfile = NetcdfFiles.open(gribFilePath)) {
            Variable uWind = findVariable(ncfile, "u-component_of_wind", level);
            Variable vWind = findVariable(ncfile, "v-component_of_wind", level);
            Variable latVar = ncfile.findVariable("lat");
            Variable lonVar = ncfile.findVariable("lon");
            
            if (uWind == null || vWind == null || latVar == null || lonVar == null) {
                throw new IOException("Variáveis de vento não encontradas");
            }
            
            Array uData = uWind.read();
            Array vData = vWind.read();
            Array latData = latVar.read();
            Array lonData = lonVar.read();
            
            int[] shape = uData.getShape();
            Index index = uData.getIndex();
            
            // Sem amostragem - todos os pontos para análise séria
            for (int i = 0; i < shape[0]; i++) {
                for (int j = 0; j < shape[1]; j++) {
                    double lat = latData.getDouble(i);
                    double lon = lonData.getDouble(j);
                    double u = uData.getDouble(index.set(i, j));
                    double v = vData.getDouble(index.set(i, j));
                    
                    if (!Double.isNaN(u) && !Double.isNaN(v)) {
                        windBarbs.add(new WindBarb(lat, lon, u, v));
                    }
                }
            }
        }
        
        return windBarbs;
    }
    
    public List<GridData> extractWindMagnitude(String gribFilePath, String level) throws IOException {
        List<GridData> magnitude = new ArrayList<>();
        
        try (NetcdfFile ncfile = NetcdfFiles.open(gribFilePath)) {
            Variable uWind = findVariable(ncfile, "u-component_of_wind", level);
            Variable vWind = findVariable(ncfile, "v-component_of_wind", level);
            Variable latVar = ncfile.findVariable("lat");
            Variable lonVar = ncfile.findVariable("lon");
            
            if (uWind == null || vWind == null) return magnitude;
            
            Array uData = uWind.read();
            Array vData = vWind.read();
            Array latData = latVar.read();
            Array lonData = lonVar.read();
            
            int[] shape = uData.getShape();
            Index index = uData.getIndex();
            
            for (int i = 0; i < shape[0]; i++) {
                for (int j = 0; j < shape[1]; j++) {
                    double lat = latData.getDouble(i);
                    double lon = lonData.getDouble(j);
                    double u = uData.getDouble(index.set(i, j));
                    double v = vData.getDouble(index.set(i, j));
                    
                    if (!Double.isNaN(u) && !Double.isNaN(v)) {
                        double speed = Math.sqrt(u*u + v*v) * 1.94384; // nós
                        magnitude.add(new GridData(lat, lon, speed, "WIND_SPEED", level, "knots"));
                    }
                }
            }
        }
        
        return magnitude;
    }
    
    public List<GridData> extractCAPE(String gribFilePath) throws IOException {
        return extractSingleVariable(gribFilePath, "Convective_available_potential_energy", "surface", "J/kg");
    }
    
    public List<GridData> extractIcing(String gribFilePath, String level) throws IOException {
        return extractSingleVariable(gribFilePath, "Categorical_ice_pellets", level, "probability");
    }
    
    public List<GridData> extractTurbulence(String gribFilePath, String level) throws IOException {
        return extractSingleVariable(gribFilePath, "Vertical_velocity_shear", level, "1/s");
    }
    
    private List<GridData> extractSingleVariable(String gribFilePath, String varName, String level, String units) throws IOException {
        List<GridData> data = new ArrayList<>();
        
        try (NetcdfFile ncfile = NetcdfFiles.open(gribFilePath)) {
            Variable var = findVariable(ncfile, varName, level);
            Variable latVar = ncfile.findVariable("lat");
            Variable lonVar = ncfile.findVariable("lon");
            
            if (var == null) return data;
            
            Array varData = var.read();
            Array latData = latVar.read();
            Array lonData = lonVar.read();
            
            int[] shape = varData.getShape();
            Index index = varData.getIndex();
            
            for (int i = 0; i < shape[0]; i++) {
                for (int j = 0; j < shape[1]; j++) {
                    double lat = latData.getDouble(i);
                    double lon = lonData.getDouble(j);
                    double value = varData.getDouble(index.set(i, j));
                    
                    if (!Double.isNaN(value)) {
                        data.add(new GridData(lat, lon, value, varName, level, units));
                    }
                }
            }
        }
        
        return data;
    }
    
    private Variable findVariable(NetcdfFile ncfile, String baseName, String level) {
        String[] possibleNames = {
            baseName + "_" + level,
            baseName + "_isobaric",
            baseName.replace("_", "").toUpperCase() + "_" + level,
            baseName.toUpperCase(),
            "UGRD_" + level,
            "VGRD_" + level,
            "CAPE_surface",
            "CICEP_" + level,
            "VWSH_" + level
        };
        
        for (String name : possibleNames) {
            Variable var = ncfile.findVariable(name);
            if (var != null) return var;
        }
        
        return null;
    }
    
    public String convertToGeoJSON(List<?> data, String type) {
        StringBuilder json = new StringBuilder();
        json.append("{\"type\":\"FeatureCollection\",\"features\":[");
        
        boolean first = true;
        for (Object item : data) {
            if (!first) json.append(",");
            first = false;
            
            if (item instanceof WindBarb) {
                WindBarb barb = (WindBarb) item;
                json.append(String.format(
                    "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.4f,%.4f]},\"properties\":{\"type\":\"wind_barb\",\"speed\":%.1f,\"direction\":%.0f,\"u\":%.2f,\"v\":%.2f}}",
                    barb.lon, barb.lat, barb.speed, barb.direction, barb.u, barb.v
                ));
            } else if (item instanceof GridData) {
                GridData grid = (GridData) item;
                json.append(String.format(
                    "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.4f,%.4f]},\"properties\":{\"type\":\"%s\",\"value\":%.3f,\"variable\":\"%s\",\"level\":\"%s\",\"units\":\"%s\"}}",
                    grid.lon, grid.lat, type, grid.value, grid.variable, grid.level, grid.units
                ));
            }
        }
        
        json.append("]}");
        return json.toString();
    }
}
