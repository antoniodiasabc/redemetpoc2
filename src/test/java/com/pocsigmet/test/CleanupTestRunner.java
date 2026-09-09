package com.pocsigmet.test;

import com.pocsigmet.scheduler.DataCleanupScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CleanupTestRunner implements CommandLineRunner {

    @Autowired
    private DataCleanupScheduler cleanupScheduler;

    @Override
    public void run(String... args) throws Exception {
        if (args.length > 0 && "test-cleanup".equals(args[0])) {
            System.out.println("🧪 EXECUTANDO TESTE DE LIMPEZA...");
            cleanupScheduler.cleanupOldData();
            System.out.println("✅ TESTE CONCLUÍDO!");
            System.exit(0);
        }
    }
}
