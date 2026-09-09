package com.pocsigmet;

import com.pocsigmet.service.AerodromoService;
import com.pocsigmet.entity.Aerodromo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TestAerodromoDatabase implements CommandLineRunner {
    
    @Autowired
    private AerodromoService aerodromoService;
    
    @Override
    public void run(String... args) throws Exception {
        try {
            System.out.println("🔍 TESTANDO CONEXÃO COM BANCO ORACLE...");
            
            // Teste 1: Estatísticas gerais
            aerodromoService.imprimirEstatisticas();
            
            // Teste 2: Buscar aeródromos do Brasil (primeiros 10)
            System.out.println("\n🇧🇷 AERÓDROMOS DO BRASIL (primeiros 10):");
            List<Aerodromo> aerodromosBrasil = aerodromoService.getAerodromosBrasil();
            aerodromosBrasil.stream().limit(10).forEach(System.out::println);
            
            // Teste 3: Buscar aeródromo específico
            System.out.println("\n🛩️ TESTANDO AERÓDROMO ESPECÍFICO (SBBR):");
            Aerodromo sbbr = aerodromoService.getAerodromoPorIcao("SBBR");
            if (sbbr != null) {
                System.out.println("✅ Encontrado: " + sbbr);
            } else {
                System.out.println("❌ SBBR não encontrado");
            }
            
            // Teste 4: Aeródromos ativos do Brasil
            System.out.println("\n✅ AERÓDROMOS ATIVOS DO BRASIL:");
            List<Aerodromo> ativosBrasil = aerodromoService.getAerodromosBrasilAtivos();
            System.out.println("Total ativos: " + ativosBrasil.size());
            
            System.out.println("\n✅ TESTE CONCLUÍDO COM SUCESSO!");
            
        } catch (Exception e) {
            System.err.println("❌ ERRO NO TESTE: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
