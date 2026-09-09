package com.pocsigmet.scheduler;

import org.springframework.stereotype.Component;

/**
 * Warmup do cache METAR gerenciado pelo scheduler manual em PocSigmetApplication
 * (com lock Redis para coordenação entre containers).
 * Esta classe existe apenas para compatibilidade com o contexto Spring.
 */
@Component
public class MetarCacheWarmupScheduler {
}
