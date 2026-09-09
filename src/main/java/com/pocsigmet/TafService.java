package com.pocsigmet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TafService {
    
    private static final String BASE_URL = "https://opmet.decea.mil.br/redemet/consulta_redemet";
    private static final String AUTH_URL = "https://opmet.decea.mil.br/adm/login";
    @org.springframework.beans.factory.annotation.Value("${app.redemet.username:redemetwebservice}")
    private String username;
    @org.springframework.beans.factory.annotation.Value("${app.redemet.password:Mudar12345@}")
    private String password;
    
    private final HttpClient client;
    private String token;
    
    public TafService() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    public String authenticate() throws IOException, InterruptedException {
        String loginData = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(AUTH_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(loginData))
            .timeout(Duration.ofSeconds(10))
            .build();
            
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonResponse = mapper.readTree(response.body());
            String authHeader = jsonResponse.get("authorization").asText();
            this.token = authHeader.replace("Bearer ", "");
            return this.token;
        }
        throw new RuntimeException("Falha na autenticação: " + response.statusCode());
    }
    
    public String obterTaf(String icao) throws IOException, InterruptedException {
        if (token == null) {
            authenticate();
        }
        
        String dataIni = "2026040112";
        String dataFim = "2026040212";
        
        String endpoint = String.format("?local=%s&msg=TAF&data_ini=%s&data_fim=%s&data_hora=nao", 
                                       icao, dataIni, dataFim);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + endpoint))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "text/plain")
            .POST(HttpRequest.BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(10))
            .build();
            
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return response.body();
        }
        return "";
    }
}
