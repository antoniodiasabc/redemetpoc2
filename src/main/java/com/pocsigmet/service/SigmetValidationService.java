package com.pocsigmet.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;

@Service
public class SigmetValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(SigmetValidationService.class);
    
    // Regex para coordenadas em diferentes formatos
    private static final Pattern LAT_LONG_DECIMAL = Pattern.compile(
        "(-?\\d{1,2}\\.\\d+)\\s*,?\\s*(-?\\d{1,3}\\.\\d+)"
    );
    
    private static final Pattern LAT_LONG_DMS = Pattern.compile(
        "([NS])?(\\d{2})(\\d{2})(\\d{2})?\\s*([EW])?(\\d{3})(\\d{2})(\\d{2})?"
    );
    
    private static final Pattern COORD_AVIATION = Pattern.compile(
        "(\\d{4}[NS]\\d{5}[EW])"
    );
    
    // FIRs válidas do Brasil
    private static final Set<String> VALID_FIRS = Set.of(
        "SBAZ", "SBAO", "SBBS", "SBCW", "SBRE"
    );
    
    // Tipos de fenômenos SIGMET
    private static final Set<String> SIGMET_PHENOMENA = Set.of(
        "TS", "TSGR", "TC", "SEV TURB", "SEV ICE", "SEV MTW", 
        "HVY DS", "HVY SS", "VA", "RDOACT CLD"
    );
    
    public ValidationResult validateSigmet(Map<String, Object> sigmet) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validar FIR
        String fir = (String) sigmet.get("fir");
        if (!isValidFir(fir)) {
            errors.add("FIR inválida: " + fir);
        }
        
        // Validar coordenadas
        String coordinates = (String) sigmet.get("coordinates");
        if (coordinates != null) {
            CoordinateValidation coordResult = validateCoordinates(coordinates);
            if (!coordResult.isValid()) {
                errors.addAll(coordResult.getErrors());
            }
            warnings.addAll(coordResult.getWarnings());
        }
        
        // Validar fenômeno
        String phenomenon = (String) sigmet.get("phenomenon");
        if (!isValidPhenomenon(phenomenon)) {
            warnings.add("Fenômeno não reconhecido: " + phenomenon);
        }
        
        // Validar níveis de voo
        String flightLevels = (String) sigmet.get("flightLevels");
        if (flightLevels != null && !isValidFlightLevel(flightLevels)) {
            errors.add("Níveis de voo inválidos: " + flightLevels);
        }
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }
    
    public CoordinateValidation validateCoordinates(String coordinates) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Coordinate> parsedCoords = new ArrayList<>();
        
        if (coordinates == null || coordinates.trim().isEmpty()) {
            errors.add("Coordenadas não fornecidas");
            return new CoordinateValidation(false, errors, warnings, parsedCoords);
        }
        
        // Tentar diferentes formatos
        List<Coordinate> coords = parseCoordinates(coordinates);
        
        for (Coordinate coord : coords) {
            // Validar limites geográficos do Brasil
            if (!isWithinBrazilianBounds(coord)) {
                warnings.add(String.format("Coordenada fora dos limites do Brasil: %.4f, %.4f", 
                    coord.latitude, coord.longitude));
            }
            
            // Validar precisão
            if (coord.precision < 0.01) {
                warnings.add("Coordenada com baixa precisão");
            }
        }
        
        if (coords.isEmpty()) {
            errors.add("Nenhuma coordenada válida encontrada em: " + coordinates);
        }
        
        return new CoordinateValidation(errors.isEmpty(), errors, warnings, coords);
    }
    
    private List<Coordinate> parseCoordinates(String coordinates) {
        List<Coordinate> coords = new ArrayList<>();
        
        // Formato decimal (lat, lon)
        Matcher decimalMatcher = LAT_LONG_DECIMAL.matcher(coordinates);
        while (decimalMatcher.find()) {
            try {
                double lat = Double.parseDouble(decimalMatcher.group(1));
                double lon = Double.parseDouble(decimalMatcher.group(2));
                coords.add(new Coordinate(lat, lon, 0.001));
            } catch (NumberFormatException e) {
                logger.warn("Erro ao parsear coordenada decimal: {}", e.getMessage());
            }
        }
        
        // Formato aviação (DDMMN DDDMMW)
        Matcher aviationMatcher = COORD_AVIATION.matcher(coordinates);
        while (aviationMatcher.find()) {
            Coordinate coord = parseAviationCoordinate(aviationMatcher.group(1));
            if (coord != null) {
                coords.add(coord);
            }
        }
        
        return coords;
    }
    
    private Coordinate parseAviationCoordinate(String coord) {
        if (coord.length() != 11) return null;
        
        try {
            // DDMMN DDDMMW
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
            
            return new Coordinate(lat, lon, 1.0/60.0); // Precisão de 1 minuto
            
        } catch (Exception e) {
            logger.warn("Erro ao parsear coordenada aviação: {}", e.getMessage());
            return null;
        }
    }
    
    private boolean isValidFir(String fir) {
        return fir != null && VALID_FIRS.contains(fir.toUpperCase());
    }
    
    private boolean isValidPhenomenon(String phenomenon) {
        if (phenomenon == null) return false;
        return SIGMET_PHENOMENA.stream()
            .anyMatch(p -> phenomenon.toUpperCase().contains(p));
    }
    
    private boolean isValidFlightLevel(String flightLevels) {
        if (flightLevels == null) return true;
        
        // Regex para níveis de voo (FL100, SFC/FL200, etc.)
        Pattern flPattern = Pattern.compile("(SFC|FL\\d{3}|\\d{4}FT)");
        return flPattern.matcher(flightLevels.toUpperCase()).find();
    }
    
    private boolean isWithinBrazilianBounds(Coordinate coord) {
        // Limites aproximados do Brasil
        return coord.latitude >= -35.0 && coord.latitude <= 6.0 &&
               coord.longitude >= -75.0 && coord.longitude <= -30.0;
    }
    
    // Classes auxiliares
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        
        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
    }
    
    public static class CoordinateValidation {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        private final List<Coordinate> coordinates;
        
        public CoordinateValidation(boolean valid, List<String> errors, 
                                  List<String> warnings, List<Coordinate> coordinates) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
            this.coordinates = coordinates;
        }
        
        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public List<Coordinate> getCoordinates() { return coordinates; }
    }
    
    public static class Coordinate {
        public final double latitude;
        public final double longitude;
        public final double precision;
        
        public Coordinate(double latitude, double longitude, double precision) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.precision = precision;
        }
    }
}
