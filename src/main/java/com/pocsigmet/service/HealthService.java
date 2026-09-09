package com.pocsigmet.service;

/**
 * Service responsável pelo health check da aplicação.
 * Extraído de PocSigmetApplication.HealthHandler (Fase 2 refactoring).
 */
public class HealthService {

    /**
     * Retorna JSON com status de saúde da aplicação.
     */
    public String getHealthStatus() {
        return "{\"status\":\"UP\",\"service\":\"POC SIGMET\",\"timestamp\":\"" + 
            java.time.Instant.now() + "\"}";
    }
}
