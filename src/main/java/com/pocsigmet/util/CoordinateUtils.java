package com.pocsigmet.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Utilitários para validação de coordenadas aeronáuticas
 */
public class CoordinateUtils {
    
    // Regex para diferentes formatos de coordenadas
    public static final Pattern DECIMAL_COORD = Pattern.compile(
        "(-?\\d{1,2}\\.\\d+)\\s*,?\\s*(-?\\d{1,3}\\.\\d+)"
    );
    
    public static final Pattern AVIATION_COORD = Pattern.compile(
        "(\\d{4}[NS]\\d{5}[EW])"
    );
    
    public static final Pattern DMS_COORD = Pattern.compile(
        "(\\d{1,2})°(\\d{1,2})'(\\d{1,2})\"([NS])\\s*(\\d{1,3})°(\\d{1,2})'(\\d{1,2})\"([EW])"
    );
    
    // Limites geográficos do Brasil
    public static final double BRAZIL_MIN_LAT = -35.0;
    public static final double BRAZIL_MAX_LAT = 6.0;
    public static final double BRAZIL_MIN_LON = -75.0;
    public static final double BRAZIL_MAX_LON = -30.0;
    
    /**
     * Valida se coordenadas estão dentro dos limites do Brasil
     */
    public static boolean isWithinBrazil(double lat, double lon) {
        return lat >= BRAZIL_MIN_LAT && lat <= BRAZIL_MAX_LAT &&
               lon >= BRAZIL_MIN_LON && lon <= BRAZIL_MAX_LON;
    }
    
    /**
     * Converte coordenada aviação (DDMMN DDDMMW) para decimal
     */
    public static double[] parseAviationCoordinate(String coord) {
        if (coord.length() != 11) return null;
        
        try {
            int latDeg = Integer.parseInt(coord.substring(0, 2));
            int latMin = Integer.parseInt(coord.substring(2, 4));
            char latDir = coord.charAt(4);
            
            int lonDeg = Integer.parseInt(coord.substring(5, 8));
            int lonMin = Integer.parseInt(coord.substring(8, 10));
            char lonDir = coord.charAt(10);
            
            double lat = latDeg + latMin / 60.0;
            if (latDir == 'S') lat = -lat;
            
            double lon = lonDeg + lonMin / 60.0;
            if (lonDir == 'W') lon = -lon;
            
            return new double[]{lat, lon};
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Valida formato de coordenada
     */
    public static boolean isValidCoordinateFormat(String coord) {
        return DECIMAL_COORD.matcher(coord).find() ||
               AVIATION_COORD.matcher(coord).find() ||
               DMS_COORD.matcher(coord).find();
    }
}
