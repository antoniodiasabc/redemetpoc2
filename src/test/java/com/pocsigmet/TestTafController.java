package com.pocsigmet;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class TestTafController implements HttpHandler {
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        
        try {
            // Extrair parâmetro icaocode da query string
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            String icaoCode = params.get("icaocode");
            
            if (icaoCode == null || icaoCode.trim().isEmpty()) {
                String error = "Parâmetro 'icaocode' é obrigatório. Exemplo: /testtaf?icaocode=SBGR";
                exchange.sendResponseHeaders(400, error.getBytes("UTF-8").length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(error.getBytes("UTF-8"));
                }
                return;
            }
            
            // Testar TAF
            TafService tafService = new TafService();
            
            System.out.println("🧪 TESTE TAF - ICAO: " + icaoCode);
            String tafResult = tafService.obterTaf(icaoCode);
            
            String response;
            if (tafResult != null && !tafResult.trim().isEmpty()) {
                response = "✅ TAF encontrado para " + icaoCode + ":\n\n" + tafResult;
            } else {
                response = "❌ TAF não disponível para " + icaoCode;
            }
            
            exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes("UTF-8"));
            }
            
        } catch (Exception e) {
            String error = "❌ Erro: " + e.getMessage();
            e.printStackTrace();
            exchange.sendResponseHeaders(500, error.getBytes("UTF-8").length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(error.getBytes("UTF-8"));
            }
        }
    }
    
    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    try {
                        String key = URLDecoder.decode(keyValue[0], "UTF-8");
                        String value = URLDecoder.decode(keyValue[1], "UTF-8");
                        params.put(key, value);
                    } catch (Exception e) {
                        // Ignorar parâmetros malformados
                    }
                }
            }
        }
        return params;
    }
}
