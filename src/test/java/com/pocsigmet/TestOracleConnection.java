package com.pocsigmet;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestOracleConnection {
    
    public static void main(String[] args) {
        String url = "jdbc:oracle:thin:@10.103.3.43:1521:OPMETDB";
        String username = "novoopmet";
        String password = "mudar123";
        
        try {
            System.out.println("🔍 TESTANDO CONEXÃO ORACLE...");
            System.out.println("URL: " + url);
            System.out.println("User: " + username);
            
            // Carregar driver
            Class.forName("oracle.jdbc.OracleDriver");
            
            // Conectar
            Connection conn = DriverManager.getConnection(url, username, password);
            System.out.println("✅ Conexão estabelecida!");
            
            // Testar query simples
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as total FROM tb_localidade");
            
            if (rs.next()) {
                int total = rs.getInt("total");
                System.out.println("📊 Total de aeródromos: " + total);
            }
            
            // Testar estrutura da tabela
            rs = stmt.executeQuery("SELECT * FROM tb_localidade WHERE ROWNUM <= 5");
            System.out.println("\n🛩️ PRIMEIROS 5 AERÓDROMOS:");
            
            while (rs.next()) {
                // Tentar diferentes nomes de colunas
                try {
                    String icao = rs.getString("ICAO");
                    String nome = rs.getString("NOME");
                    Double lat = rs.getDouble("LATITUDE");
                    Double lon = rs.getDouble("LONGITUDE");
                    String pais = rs.getString("PAIS");
                    
                    System.out.printf("   %s - %s (%.4f, %.4f) - %s%n", icao, nome, lat, lon, pais);
                } catch (Exception e) {
                    System.out.println("   Erro lendo registro: " + e.getMessage());
                    // Mostrar todas as colunas disponíveis
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        String colName = rs.getMetaData().getColumnName(i);
                        String colValue = rs.getString(i);
                        System.out.println("     " + colName + " = " + colValue);
                    }
                    break;
                }
            }
            
            rs.close();
            stmt.close();
            conn.close();
            
            System.out.println("\n✅ TESTE CONCLUÍDO COM SUCESSO!");
            
        } catch (Exception e) {
            System.err.println("❌ ERRO: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
