package com.pocsigmet;

import java.util.*;

public class MockSigmetClient {
    
    public String getSigmetsJson() {
        List<Map<String, Object>> mockSigmets = new ArrayList<>();
        
        // Mock SIGMET para SBAZ
        Map<String, Object> sigmet1 = new HashMap<>();
        sigmet1.put("type", "Feature");
        
        Map<String, Object> geometry1 = new HashMap<>();
        geometry1.put("type", "Polygon");
        List<List<Double>> coords1 = Arrays.asList(
            Arrays.asList(-60.0, -5.0),
            Arrays.asList(-58.0, -5.0),
            Arrays.asList(-58.0, -3.0),
            Arrays.asList(-60.0, -3.0),
            Arrays.asList(-60.0, -5.0)
        );
        geometry1.put("coordinates", Arrays.asList(coords1));
        sigmet1.put("geometry", geometry1);
        
        Map<String, Object> props1 = new HashMap<>();
        props1.put("fir", "SBAZ");
        props1.put("text", "SBAZ SIGMET 1 VALID 171930/172330 SBAZ - SBAZ AMAZONICA FIR EMBD TS");
        sigmet1.put("properties", props1);
        
        // Mock SIGMET para SBBS
        Map<String, Object> sigmet2 = new HashMap<>();
        sigmet2.put("type", "Feature");
        
        Map<String, Object> geometry2 = new HashMap<>();
        geometry2.put("type", "Polygon");
        List<List<Double>> coords2 = Arrays.asList(
            Arrays.asList(-48.0, -15.0),
            Arrays.asList(-46.0, -15.0),
            Arrays.asList(-46.0, -13.0),
            Arrays.asList(-48.0, -13.0),
            Arrays.asList(-48.0, -15.0)
        );
        geometry2.put("coordinates", Arrays.asList(coords2));
        sigmet2.put("geometry", geometry2);
        
        Map<String, Object> props2 = new HashMap<>();
        props2.put("fir", "SBBS");
        props2.put("text", "SBBS SIGMET 2 VALID 171930/172330 SBBS - SBBS BRASILIA FIR EMBD TS");
        sigmet2.put("properties", props2);
        
        mockSigmets.add(sigmet1);
        mockSigmets.add(sigmet2);
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(mockSigmets);
        } catch (Exception e) {
            return "[]";
        }
    }
}
