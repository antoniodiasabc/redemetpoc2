package com.pocsigmet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Garante que o TTL do cache METAR no Redis seja maior que o intervalo do warmup,
 * evitando janela onde chaves expiram antes do próximo ciclo de atualização.
 */
public class MetarTtlWarmupTest {

    // intervalo do warmup em segundos (fixedDelay = 300_000 ms)
    private static final long WARMUP_INTERVAL_SECONDS = 300;

    @Test
    void ttlDeveSerMaiorQueIntervaloDoWarmup() {
        assertTrue(
            RedisMetarCacheService.METAR_TTL_SECONDS > WARMUP_INTERVAL_SECONDS,
            "METAR_TTL_SECONDS (" + RedisMetarCacheService.METAR_TTL_SECONDS +
            "s) deve ser maior que o intervalo do warmup (" + WARMUP_INTERVAL_SECONDS + "s)"
        );
    }
}
