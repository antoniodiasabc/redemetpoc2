package com.pocsigmet.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class SigmetParsingService {
    
    private static final Logger logger = LoggerFactory.getLogger(SigmetParsingService.class);
    
    @Autowired
    private SigmetValidationService validationService;
    
    // Regex patterns para parsing SIGMET
    private static final Pattern SIGMET_HEADER = Pattern.compile(
        "(\\w{4})\\s+SIGMET\\s+(\\w+)\\s+VALID\\s+(\\d{6})/(\\d{6})"
    );
    
    private static final Pattern FIR_PATTERN = Pattern.compile(
        "(\\w{4})\\s+FIR"
    );
    
    private static final Pattern PHENOMENON_PATTERN = Pattern.compile(
        "(TS|TSGR|TC|SEV TURB|SEV ICE|SEV MTW|HVY DS|HVY SS|VA|RDOACT CLD)"
    );
    
    private static final Pattern MOVEMENT_PATTERN = Pattern.compile(
        "MOV\\s+(N|S|E|W|NE|NW|SE|SW|STNR)\\s*(\\d+KT)?"
    );
    
    private static final Pattern INTENSITY_PATTERN = Pattern.compile(
        "(INTSF|WKN|NC)"
    );
    
    public ParsedSigmet parseSigmetText(String sigmetText) {
        if (sigmetText == null || sigmetText.trim().isEmpty()) {
            return new ParsedSigmet(false, "Texto SIGMET vazio", null);
        }
        
        try {
            Map<String, Object> sigmetData = new HashMap<>();
            List<String> parseWarnings = new ArrayList<>();
            
            // Parse header
            parseHeader(sigmetText, sigmetData, parseWarnings);
            
            // Parse FIR
            parseFir(sigmetText, sigmetData, parseWarnings);
            
            // Parse phenomenon
            parsePhenomenon(sigmetText, sigmetData, parseWarnings);
            
            // Parse coordinates
            parseCoordinates(sigmetText, sigmetData, parseWarnings);
            
            // Parse movement
            parseMovement(sigmetText, sigmetData, parseWarnings);
            
            // Parse intensity
            parseIntensity(sigmetText, sigmetData, parseWarnings);
            
            // Parse flight levels
            parseFlightLevels(sigmetText, sigmetData, parseWarnings);
            
            // Validar dados parseados
            SigmetValidationService.ValidationResult validation = 
                validationService.validateSigmet(sigmetData);
            
            parseWarnings.addAll(validation.getWarnings());
            
            if (!validation.isValid()) {
                return new ParsedSigmet(false, 
                    "Validação falhou: " + String.join(", ", validation.getErrors()), 
                    sigmetData);
            }
            
            sigmetData.put("parseWarnings", parseWarnings);
            sigmetData.put("originalText", sigmetText);
            sigmetData.put("parsedAt", LocalDateTime.now().toString());
            
            return new ParsedSigmet(true, "Parse bem-sucedido", sigmetData);
            
        } catch (Exception e) {
            logger.error("Erro ao parsear SIGMET: {}", e.getMessage());
            return new ParsedSigmet(false, "Erro no parsing: " + e.getMessage(), null);
        }
    }
    
    private void parseHeader(String text, Map<String, Object> data, List<String> warnings) {
        Matcher matcher = SIGMET_HEADER.matcher(text);
        if (matcher.find()) {
            data.put("icao", matcher.group(1));
            data.put("sigmetId", matcher.group(2));
            data.put("validFrom", matcher.group(3));
            data.put("validTo", matcher.group(4));
        } else {
            warnings.add("Header SIGMET não encontrado");
        }
    }
    
    private void parseFir(String text, Map<String, Object> data, List<String> warnings) {
        Matcher matcher = FIR_PATTERN.matcher(text);
        if (matcher.find()) {
            data.put("fir", matcher.group(1));
        } else {
            warnings.add("FIR não identificada");
        }
    }
    
    private void parsePhenomenon(String text, Map<String, Object> data, List<String> warnings) {
        Matcher matcher = PHENOMENON_PATTERN.matcher(text);
        if (matcher.find()) {
            data.put("phenomenon", matcher.group(1));
        } else {
            warnings.add("Fenômeno não identificado");
        }
    }
    
    private void parseCoordinates(String text, Map<String, Object> data, List<String> warnings) {
        // Extrair coordenadas do texto
        List<String> coordLines = Arrays.stream(text.split("\\n"))
            .filter(line -> line.matches(".*\\d{4}[NS]\\d{5}[EW].*"))
            .toList();
        
        if (!coordLines.isEmpty()) {
            String coordinates = String.join(" ", coordLines);
            data.put("coordinates", coordinates);
            
            // Validar coordenadas
            SigmetValidationService.CoordinateValidation coordValidation = 
                validationService.validateCoordinates(coordinates);
            
            if (coordValidation.isValid()) {
                data.put("parsedCoordinates", coordValidation.getCoordinates());
            }
            warnings.addAll(coordValidation.getWarnings());
        } else {
            warnings.add("Coordenadas não encontradas");
        }
    }
    
    private void parseMovement(String text, Map<String, Object> data, List<String> warnings) {
        Matcher matcher = MOVEMENT_PATTERN.matcher(text);
        if (matcher.find()) {
            data.put("movement", matcher.group(1));
            if (matcher.group(2) != null) {
                data.put("speed", matcher.group(2));
            }
        }
    }
    
    private void parseIntensity(String text, Map<String, Object> data, List<String> warnings) {
        Matcher matcher = INTENSITY_PATTERN.matcher(text);
        if (matcher.find()) {
            data.put("intensity", matcher.group(1));
        }
    }
    
    private void parseFlightLevels(String text, Map<String, Object> data, List<String> warnings) {
        // Regex para níveis de voo
        Pattern flPattern = Pattern.compile("(SFC|FL\\d{3}|\\d{4}FT)(?:/(SFC|FL\\d{3}|\\d{4}FT))?");
        Matcher matcher = flPattern.matcher(text);
        
        if (matcher.find()) {
            String flightLevels = matcher.group(0);
            data.put("flightLevels", flightLevels);
            
            // Parse níveis individuais
            if (matcher.group(2) != null) {
                data.put("bottomLevel", matcher.group(1));
                data.put("topLevel", matcher.group(2));
            } else {
                data.put("level", matcher.group(1));
            }
        }
    }
    
    public static class ParsedSigmet {
        private final boolean success;
        private final String message;
        private final Map<String, Object> data;
        
        public ParsedSigmet(boolean success, String message, Map<String, Object> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getData() { return data; }
    }
}
