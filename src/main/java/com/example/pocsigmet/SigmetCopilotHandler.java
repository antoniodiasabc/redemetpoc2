package com.example.pocsigmet;

public class SigmetCopilotHandler {

    public String processImage() {
        // Retorna GeoJSON de exemplo por enquanto
        return "{\"type\": \"FeatureCollection\", \"features\": [{\"type\": \"Feature\", \"geometry\": {\"type\": \"Polygon\", \"coordinates\": [[[-50.0, -10.0], [-45.0, -10.0], [-45.0, -15.0], [-50.0, -15.0], [-50.0, -10.0]]]}, \"properties\": {\"id\": 1, \"severity\": \"COPILOT\"}}]}";
    }
}
