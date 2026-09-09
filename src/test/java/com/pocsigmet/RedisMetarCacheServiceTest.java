package com.pocsigmet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;

public class RedisMetarCacheServiceTest {

    @BeforeEach
    void clearMemoryCache() {
        RedisMetarCacheService.getMemoryCache().clear();
    }

    @Test
    void testCacheStatsMethod() {
        String stats = RedisMetarCacheService.getCacheStats();
        assertNotNull(stats);
        assertTrue(stats.contains("Redis:"));
    }

    @Test
    void getAllCached_returnsMemoryCacheWhenRedisUnavailable() {
        RedisMetarCacheService.getMemoryCache().put("metar:SBSP",
            new RedisMetarCacheService.CachedMetarData("VFR", "METAR SBSP 010000Z 00000KT 9999 FEW020 25/18 Q1013", "", false));
        var result = RedisMetarCacheService.getAllCached();
        assertTrue(result.containsKey("SBSP"));
        assertEquals("VFR", result.get("SBSP").condition);
    }

    @Test
    void getAllCached_stripsMetarPrefixFromKey() {
        RedisMetarCacheService.getMemoryCache().put("metar:SBGR",
            new RedisMetarCacheService.CachedMetarData("IFR", "METAR SBGR 010000Z 00000KT 0800 FG OVC002 18/17 Q1010", "", false));
        var result = RedisMetarCacheService.getAllCached();
        assertFalse(result.containsKey("metar:SBGR"));
        assertTrue(result.containsKey("SBGR"));
    }

    private String nowMetarTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return String.format("%02d%02d%02dZ", now.getDayOfMonth(), now.getHour(), now.getMinute());
    }

    @Test
    void getMetarAlerts_deterministicOnSameCache() {
        String t = nowMetarTime();
        RedisMetarCacheService.getMemoryCache().put("metar:SBSP",
            new RedisMetarCacheService.CachedMetarData("IFR", "METAR SBSP " + t + " 00000KT 0800 TSRA FG OVC002 18/17 Q1010", "", false));
        var svc = new com.pocsigmet.service.MetarService(null, null);
        assertEquals(svc.getMetarAlerts(), svc.getMetarAlerts());
    }

    @Test
    void getMetarAlerts_includesAlertForRecentMetar() {
        String t = nowMetarTime();
        RedisMetarCacheService.getMemoryCache().put("metar:SBSP",
            new RedisMetarCacheService.CachedMetarData("IFR", "METAR SBSP " + t + " 00000KT 0800 TSRA OVC002 18/17 Q1010", "", false));
        String result = new com.pocsigmet.service.MetarService(null, null).getMetarAlerts();
        assertTrue(result.contains("SBSP"));
        assertTrue(result.contains("TSRA"));
    }

    @Test
    void getMetarAlerts_excludesStaleMetarOver2h() {
        RedisMetarCacheService.getMemoryCache().put("metar:SBSP",
            new RedisMetarCacheService.CachedMetarData("IFR", "METAR SBSP 010000Z 00000KT 0800 TSRA OVC002 18/17 Q1010", "", false));
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        if (now.getDayOfMonth() == 1 && now.getHour() < 2) return;
        assertFalse(new com.pocsigmet.service.MetarService(null, null).getMetarAlerts().contains("SBSP"));
    }

    @Test
    void getMetarAlerts_excludesMetarWithoutPhenomena() {
        String t = nowMetarTime();
        RedisMetarCacheService.getMemoryCache().put("metar:SBCT",
            new RedisMetarCacheService.CachedMetarData("VFR", "METAR SBCT " + t + " 00000KT 9999 FEW020 25/18 Q1013", "", false));
        assertFalse(new com.pocsigmet.service.MetarService(null, null).getMetarAlerts().contains("SBCT"));
    }

    @Test
    void getMetarAlerts_detectsFogVariants() {
        String t = nowMetarTime();
        for (String fog : new String[]{"FG", "BCFG", "PRFG", "MIFG", "FZFG"}) {
            RedisMetarCacheService.getMemoryCache().clear();
            RedisMetarCacheService.getMemoryCache().put("metar:SBCX",
                new RedisMetarCacheService.CachedMetarData("IFR", "SPECI SBCX " + t + " 21002KT 3000 " + fog + " BKN004 Q1012=", "", false));
            String result = new com.pocsigmet.service.MetarService(null, null).getMetarAlerts();
            assertTrue(result.contains(fog), fog + " deve gerar alerta");
        }
    }

    @Test
    void getMetarAlerts_detectsGust() {
        String t = nowMetarTime();
        RedisMetarCacheService.getMemoryCache().put("metar:SBGL",
            new RedisMetarCacheService.CachedMetarData("VFR", "METAR SBGL " + t + " 18015G25KT 9999 FEW020 28/18 Q1012", "", false));
        String result = new com.pocsigmet.service.MetarService(null, null).getMetarAlerts();
        assertTrue(result.contains("SBGL"));
        assertTrue(result.contains("G25KT"));
    }
}
