package com.pocsigmet;

import com.pocsigmet.grib2.WindBarbService;
import com.pocsigmet.grib2.WindBarbData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class WindBarbServiceTest {
    
    private WindBarbService service;
    
    @BeforeEach
    public void setUp() {
        try {
            service = new WindBarbService();
            
            // Injetar dependência manualmente (como no código principal)
            com.pocsigmet.grib2.Grib2Downloader downloader = new com.pocsigmet.grib2.Grib2Downloader();
            java.lang.reflect.Field field = com.pocsigmet.grib2.WindBarbService.class.getDeclaredField("downloader");
            field.setAccessible(true);
            field.set(service, downloader);
            
        } catch (Exception e) {
            System.err.println("❌ Erro no setup: " + e.getMessage());
        }
    }
    
    @Test
    public void testGetWindBarbsFL050() {
        try {
            List<WindBarbData> result = service.getWindBarbs("fl050");
            assertNotNull(result, "WindBarbs FL050 não deve ser null");
            System.out.println("✅ FL050: " + result.size() + " barbelas");
        } catch (Exception e) {
            fail("Erro ao obter barbelas FL050: " + e.getMessage());
        }
    }
    
    @Test
    public void testGetWindBarbsFL390() {
        try {
            List<WindBarbData> result = service.getWindBarbs("fl390");
            assertNotNull(result, "WindBarbs FL390 não deve ser null");
            System.out.println("✅ FL390: " + result.size() + " barbelas");
        } catch (Exception e) {
            fail("Erro ao obter barbelas FL390: " + e.getMessage());
        }
    }
    
    @Test
    public void testWindBarbsCache() {
        try {
            // Primeira chamada
            long start1 = System.currentTimeMillis();
            List<WindBarbData> result1 = service.getWindBarbs("fl050");
            long time1 = System.currentTimeMillis() - start1;
            
            // Segunda chamada (deve usar cache)
            long start2 = System.currentTimeMillis();
            List<WindBarbData> result2 = service.getWindBarbs("fl050");
            long time2 = System.currentTimeMillis() - start2;
            
            assertNotNull(result1);
            assertNotNull(result2);
            assertEquals(result1.size(), result2.size(), "Cache deve retornar mesmo número de barbelas");
            
            System.out.println("✅ Cache test - 1ª: " + time1 + "ms, 2ª: " + time2 + "ms");
            
        } catch (Exception e) {
            fail("Erro no teste de cache: " + e.getMessage());
        }
    }
    
    @Test
    public void testWindBarbsSkip() {
        try {
            List<WindBarbData> skip1 = service.getWindBarbs("fl050", 1);
            List<WindBarbData> skip4 = service.getWindBarbs("fl050", 4);
            
            assertNotNull(skip1);
            assertNotNull(skip4);
            assertTrue(skip1.size() >= skip4.size(), "Skip 1 deve ter mais ou igual barbelas que skip 4");
            
            System.out.println("✅ Skip test - Skip1: " + skip1.size() + ", Skip4: " + skip4.size());
            
        } catch (Exception e) {
            fail("Erro no teste de skip: " + e.getMessage());
        }
    }
}
