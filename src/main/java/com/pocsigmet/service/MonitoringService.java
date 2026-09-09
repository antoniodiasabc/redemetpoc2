package com.pocsigmet.service;

import com.pocsigmet.RedisMetarCacheService;

/**
 * Service responsável por estatísticas de monitoramento e cache.
 * Extraído de PocSigmetApplication.handleMonitoringStats/handleCacheStats (Fase 2 refactoring).
 */
public class MonitoringService {

    /**
     * Retorna JSON com estatísticas de monitoramento (memória, threads, uptime).
     */
    public String getMonitoringStats() {
        Runtime runtime = Runtime.getRuntime();
        java.lang.management.MemoryMXBean memoryBean = java.lang.management.ManagementFactory.getMemoryMXBean();
        java.lang.management.ThreadMXBean threadBean = java.lang.management.ManagementFactory.getThreadMXBean();

        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;

        return String.format(
            "{\"memory_used_mb\":%d,\"memory_max_mb\":%d,\"memory_usage_percent\":%d," +
            "\"heap_used_mb\":%d,\"cpu_cores\":%d,\"thread_count\":%d," +
            "\"uptime_ms\":%d,\"timestamp\":%d}",
            usedMemory, maxMemory, (usedMemory * 100) / maxMemory,
            memoryBean.getHeapMemoryUsage().getUsed() / 1024 / 1024,
            runtime.availableProcessors(), threadBean.getThreadCount(),
            java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime(),
            System.currentTimeMillis()
        );
    }

    /**
     * Retorna JSON com estatísticas de cache Redis.
     */
    public String getCacheStats() {
        String cacheStats = RedisMetarCacheService.getCacheStats();
        return String.format(
            "{\"status\":\"active\",\"stats\":\"%s\",\"timestamp\":\"%s\",\"ttl_seconds\":300}",
            cacheStats, java.time.LocalDateTime.now().toString()
        );
    }
}
