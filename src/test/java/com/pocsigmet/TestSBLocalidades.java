package com.pocsigmet;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestSBLocalidades {
    
    public static void main(String[] args) {
        String url = "jdbc:oracle:thin:@10.103.3.43:1521:OPMETDB";
        String username = "novoopmet";
        String password = "mudar123";
        
        try {
            System.out.println("🔍 TESTANDO LOCALIDADES SB DO BRASIL...");
            
            Class.forName("oracle.jdbc.OracleDriver");
            Connection conn = DriverManager.getConnection(url, username, password);
            
            Statement stmt = conn.createStatement();
            
            // Teste 1: Contar localidades SB
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) as total FROM tb_localidade WHERE CD_LOCALIDADE LIKE 'SB%' AND ID_GEOGRAFICO = 'BZ'"
            );
            
            if (rs.next()) {
                int total = rs.getInt("total");
                System.out.println("📊 Total localidades SB Brasil: " + total);
            }
            
            // Teste 2: Primeiras 20 localidades SB
            rs = stmt.executeQuery(
                "SELECT CD_LOCALIDADE, NM_LOCALIDADE, LATITUDE, LONGITUDE, STATUS " +
                "FROM tb_localidade " +
                "WHERE CD_LOCALIDADE LIKE 'SB%' AND ID_GEOGRAFICO = 'BZ' " +
                "AND ROWNUM <= 20 " +
                "ORDER BY CD_LOCALIDADE"
            );
            
            System.out.println("\n🛩️ PRIMEIRAS 20 LOCALIDADES SB:");
            int count = 0;
            while (rs.next()) {
                count++;
                String icao = rs.getString("CD_LOCALIDADE");
                String nome = rs.getString("NM_LOCALIDADE");
                Double lat = rs.getDouble("LATITUDE");
                Double lon = rs.getDouble("LONGITUDE");
                String status = rs.getString("STATUS");
                
                System.out.printf("%2d. %s - %s (%.4f, %.4f) [%s]%n", 
                                count, icao, nome, lat, lon, status);
            }
            
            // Teste 3: Só as ativas
            rs = stmt.executeQuery(
                "SELECT COUNT(*) as total FROM tb_localidade " +
                "WHERE CD_LOCALIDADE LIKE 'SB%' AND ID_GEOGRAFICO = 'BZ' AND STATUS = 'A'"
            );
            
            if (rs.next()) {
                int totalAtivas = rs.getInt("total");
                System.out.println("\n✅ Total localidades SB ativas: " + totalAtivas);
            }
            
            rs.close();
            stmt.close();
            conn.close();
            
            System.out.println("\n✅ TESTE CONCLUÍDO!");
            
        } catch (Exception e) {
            System.err.println("❌ ERRO: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
