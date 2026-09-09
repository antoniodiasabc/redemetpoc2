package com.pocsigmet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public class TestNeighborSigmets {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Testando NeighborSigmetClient ===\n");

        NeighborSigmetClient client = new NeighborSigmetClient(new NeighborSigmetParser());
        String json = client.getNeighborSigmetsJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode features = mapper.readTree(json);

        System.out.println("Total features: " + features.size());
        System.out.println();

        Set<String> firsComSigmet = new LinkedHashSet<>();
        for (JsonNode feat : features) {
            JsonNode props = feat.get("properties");
            String fir    = props.get("fir").asText();
            String num    = props.get("sigmetNumber").asText();
            String type   = props.get("sigmetType").asText();
            String valid  = props.get("validPeriod").asText();
            String method = props.get("method").asText();
            JsonNode geom = feat.get("geometry");
            int pts = geom.get("coordinates").get(0).size();
            firsComSigmet.add(fir);
            System.out.printf("✅ %-6s SIGMET %-4s %-15s valid=%-13s método=%-15s pts=%d%n",
                fir, num, type, valid, method, pts);
        }

        System.out.println("\nFIRs com SIGMET: " + firsComSigmet);

        // Verificar SUEO e SPIM especificamente
        boolean temSUEO = firsComSigmet.contains("SUEO");
        boolean temSPIM = firsComSigmet.contains("SPIM");
        System.out.println("\nSUEO: " + (temSUEO ? "✅ presente" : "❌ ausente (sem SIGMET ativo agora)"));
        System.out.println("SPIM: " + (temSPIM ? "✅ presente" : "❌ ausente (sem SIGMET ativo agora)"));
    }
}
