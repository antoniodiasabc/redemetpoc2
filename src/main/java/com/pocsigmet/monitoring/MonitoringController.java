package com.pocsigmet.monitoring;

import org.springframework.web.bind.annotation.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class MonitoringController {
    
    @GetMapping("/monitoring/stats")
    public Map<String, Object> getStats() {
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        Map<String, Object> stats = new HashMap<>();
        
        // Memória (MB)
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        
        stats.put("memory_used_mb", usedMemory);
        stats.put("memory_free_mb", runtime.freeMemory() / 1024 / 1024);
        stats.put("memory_total_mb", runtime.totalMemory() / 1024 / 1024);
        stats.put("memory_max_mb", maxMemory);
        stats.put("memory_usage_percent", (usedMemory * 100) / maxMemory);
        stats.put("heap_used_mb", memoryBean.getHeapMemoryUsage().getUsed() / 1024 / 1024);
        stats.put("heap_max_mb", memoryBean.getHeapMemoryUsage().getMax() / 1024 / 1024);
        
        // CPU e Threads
        stats.put("cpu_cores", runtime.availableProcessors());
        stats.put("thread_count", threadBean.getThreadCount());
        stats.put("peak_thread_count", threadBean.getPeakThreadCount());
        
        // GC
        long totalGcTime = 0;
        long totalGcCount = 0;
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            totalGcTime += gcBean.getCollectionTime();
            totalGcCount += gcBean.getCollectionCount();
        }
        stats.put("gc_total_time_ms", totalGcTime);
        stats.put("gc_total_count", totalGcCount);
        
        // Sistema
        stats.put("uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
        stats.put("timestamp", System.currentTimeMillis());
        
        return stats;
    }
    
    @GetMapping("/monitoring/load-test")
    public Map<String, String> loadTest(@RequestParam(defaultValue = "100") int iterations) {
        long start = System.currentTimeMillis();
        
        // Simular carga
        for (int i = 0; i < iterations; i++) {
            Math.random();
        }
        
        long duration = System.currentTimeMillis() - start;
        
        Map<String, String> result = new HashMap<>();
        result.put("iterations", String.valueOf(iterations));
        result.put("duration_ms", String.valueOf(duration));
        result.put("ops_per_second", String.valueOf(iterations * 1000 / Math.max(duration, 1)));
        
        return result;
    }
}
