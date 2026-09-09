package com.pocsigmet.test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class TestCleanup {
    public static void main(String[] args) {
        System.out.println("🧪 TESTE MANUAL DE LIMPEZA");
        
        String dataPath = "./data";
        int retentionDays = 2;
        
        Instant cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        System.out.println("🗓️ Removendo arquivos anteriores a: " + cutoffTime);
        
        cleanupDirectory(Paths.get(dataPath, "images"), cutoffTime, "imagens");
        cleanupDirectory(Paths.get(dataPath, "grib2"), cutoffTime, "GRIB2");
        cleanupDirectory(Paths.get(dataPath), cutoffTime, "data geral");
        cleanupDirectory(Paths.get("logs"), cutoffTime, "logs");
        
        System.out.println("✅ TESTE CONCLUÍDO!");
    }
    
    private static void cleanupDirectory(Path directory, Instant cutoffTime, String type) {
        if (!Files.exists(directory)) {
            System.out.println("⚠️ Diretório " + directory + " não existe");
            return;
        }

        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.lastModifiedTime().toInstant().isBefore(cutoffTime)) {
                        try {
                            System.out.println("🗑️ Removendo: " + file);
                            Files.delete(file);
                        } catch (IOException e) {
                            System.out.println("❌ Erro ao remover " + file + ": " + e.getMessage());
                        }
                    } else {
                        System.out.println("✅ Mantendo: " + file + " (recente)");
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            
            System.out.println("🎯 Limpeza de " + type + " concluída");
            
        } catch (IOException e) {
            System.out.println("❌ Erro durante limpeza de " + type + ": " + e.getMessage());
        }
    }
}
