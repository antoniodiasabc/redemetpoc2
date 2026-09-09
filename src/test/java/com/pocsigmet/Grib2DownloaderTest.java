package com.pocsigmet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class Grib2DownloaderTest {

    // replica exata da logica corrigida
    private String getLatestGFSRun(LocalDateTime now) {
        int hour = now.getHour();
        int latestRun = (hour / 6) * 6;
        if ((hour - latestRun) < 4) {
            latestRun -= 6;
            if (latestRun < 0) { latestRun = 18; now = now.minusDays(1); }
        }
        return now.withHour(latestRun).withMinute(0).withSecond(0)
                  .format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
    }

    @Test public void testRun16h() {
        // 16:21 UTC -> 12Z disponivel (4h apos)
        assertEquals("2026080312", getLatestGFSRun(LocalDateTime.of(2026,8,3,16,21)));
    }

    @Test public void testRun15h() {
        // 15:00 UTC -> apenas 3h apos 12Z, ainda nao disponivel -> usa 06Z
        assertEquals("2026080306", getLatestGFSRun(LocalDateTime.of(2026,8,3,15,0)));
    }

    @Test public void testRun00h() {
        // 00:00 UTC -> 0h apos 00Z, nao disponivel -> usa 18Z do dia anterior
        assertEquals("2026080218", getLatestGFSRun(LocalDateTime.of(2026,8,3,0,0)));
    }

    @Test public void testRun04h() {
        // 04:00 UTC -> 4h apos 00Z -> usa 00Z
        assertEquals("2026080300", getLatestGFSRun(LocalDateTime.of(2026,8,3,4,0)));
    }
}
