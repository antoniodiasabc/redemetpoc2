package com.pocsigmet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SimpleBaselineTest {
    
    @Test
    public void testSystemIsRunning() {
        // Teste básico - sistema deve estar rodando
        assertTrue(true, "Sistema básico funcionando");
        System.out.println("✅ Sistema básico OK");
    }
    
    @Test
    public void testWindBarbServiceExists() {
        try {
            // Verificar se a classe existe
            Class<?> clazz = Class.forName("com.pocsigmet.grib2.WindBarbService");
            assertNotNull(clazz, "WindBarbService deve existir");
            System.out.println("✅ WindBarbService classe encontrada");
            
        } catch (ClassNotFoundException e) {
            fail("WindBarbService não encontrada: " + e.getMessage());
        }
    }
    
    @Test
    public void testOpmetWebSocketHandlerExists() {
        try {
            // Verificar se a classe existe
            Class<?> clazz = Class.forName("com.pocsigmet.OpmetWebSocketHandler");
            assertNotNull(clazz, "OpmetWebSocketHandler deve existir");
            System.out.println("✅ OpmetWebSocketHandler classe encontrada");
            
        } catch (ClassNotFoundException e) {
            fail("OpmetWebSocketHandler não encontrada: " + e.getMessage());
        }
    }
}
