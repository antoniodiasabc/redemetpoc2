package com.pocsigmet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;

/**
 * Dado um aeródromo, cruza METAR/TAF/SPECI + SIGMETs ativos + SigWx WAFS
 * e imprime um resumo das condições de voo nas redondezas.
 */
public class FlightBriefingTest {

    private static final String BASE = "http://localhost";
    private static final double RADIUS_DEG = 3.0; // ~330km de raio

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void briefingSUMU() throws Exception { printBriefing("SUMU"); assertTrue(true); }

    @Test
    public void briefingSBGR() throws Exception { printBriefing("SBGR"); assertTrue(true); }

    private void printBriefing(String icao) throws Exception {
        System.out.println("\n========================================");
        System.out.println("  BRIEFING METEOROLÓGICO — " + icao);
        System.out.println("========================================");

        // 1. METAR/TAF do aeródromo
        JsonNode aero = getAerodromo(icao);
        if (aero == null) { System.out.println("❌ Aeródromo não encontrado"); return; }

        double lat = aero.get("lat").asDouble();
        double lon = aero.get("lon").asDouble();
        String nome = aero.path("nome").asText(icao);

        System.out.println("\n📍 " + icao + " — " + nome);
        System.out.println("   Condição: " + aero.path("condition").asText());

        String metar = aero.path("metarText").asText("");
        String taf   = aero.path("tafText").asText("");
        boolean isSpeci = aero.path("isSpeci").asBoolean();

        if (isSpeci) System.out.println("   ⚠️  SPECI ativo");
        if (!metar.isEmpty()) System.out.println("\n📡 METAR/SPECI:\n   " + metar);
        if (!taf.isEmpty())   System.out.println("\n📋 TAF:\n   " + taf);

        // analisa fenômenos no TAF/METAR
        System.out.println("\n⚠️  Fenômenos detectados:");
        analisaFenomenos(metar, taf);

        // 2. SIGMETs ativos
        System.out.println("\n🔴 SIGMETs ativos nas redondezas:");
        List<JsonNode> sigmets = getSigmets();
        boolean temSigmet = false;
        for (JsonNode s : sigmets) {
            JsonNode props = s.get("properties");
            // verifica se o polígono do sigmet está próximo
            if (proximoAo(s.get("geometry"), lat, lon, RADIUS_DEG)) {
                System.out.println("   • [" + props.path("fir").asText() + "] "
                    + props.path("sigmetType").asText()
                    + " — válido " + props.path("validPeriod").asText());
                System.out.println("     " + props.path("text").asText());
                temSigmet = true;
            }
        }
        if (!temSigmet) System.out.println("   Nenhum SIGMET nas redondezas");

        // 3. SigWx WAFS
        System.out.println("\n🌐 SigWx WAFS nas redondezas (" + RADIUS_DEG + "° ~" + (int)(RADIUS_DEG*111) + "km):");
        List<JsonNode> sigwx = getSigwxFeatures();
        Map<String, List<String>> porTipo = new LinkedHashMap<>();
        for (JsonNode f : sigwx) {
            JsonNode props = f.get("properties");
            String tipo = props.path("type").asText();
            if (proximoAo(f.get("geometry"), lat, lon, RADIUS_DEG)) {
                String desc = descreveSigwx(tipo, props);
                porTipo.computeIfAbsent(tipo, k -> new ArrayList<>()).add(desc);
            }
        }
        if (porTipo.isEmpty()) {
            System.out.println("   Nenhum fenômeno SigWx nas redondezas");
        } else {
            porTipo.forEach((tipo, lista) -> {
                System.out.println("   " + emojiTipo(tipo) + " " + tipo + " (" + lista.size() + " área(s)):");
                lista.stream().distinct().limit(3).forEach(d -> System.out.println("     • " + d));
            });
        }

        // 4. Resumo de risco
        System.out.println("\n🎯 RESUMO DE RISCO:");
        imprimirRisco(metar, taf, temSigmet, porTipo);
        System.out.println("========================================\n");
    }

    private void analisaFenomenos(String metar, String taf) {
        String t = (metar + " " + taf).toUpperCase();
        if (t.contains("TSRA") || t.contains("+TS")) System.out.println("   ⛈️  Trovoada severa (TSRA)");
        else if (t.contains("TS"))                   System.out.println("   ⛈️  Trovoada (TS)");
        if (t.matches("(?s).*(?<![A-Z])GR(?![A-Z]).*")) System.out.println("   🧊 Granizo (GR)");
        if (t.contains("CB"))                        System.out.println("   ☁️  Cumulonimbus (CB)");
        if (t.contains("FZRA") || t.contains("FZDZ"))System.out.println("   🌧️  Chuva congelante");
        if (t.contains("LLWS") || t.contains(" WS "))System.out.println("   💨 Wind shear");
    }

    private String descreveSigwx(String tipo, JsonNode props) {
        String flU = props.path("flUpper").asText("");
        String flL = props.path("flLower").asText("");
        String fl  = (!flL.isEmpty() || !flU.isEmpty()) ? "FL" + flL + "-FL" + flU : "";
        switch (tipo) {
            case "CLOUD":
                String dist = props.path("cloudDist").asText("?");
                String label = (dist.equals("7") || dist.equals("8")) ? "FRQ CB" : "OCNL CB";
                return label + (fl.isEmpty() ? "" : " " + fl);
            case "TURBULENCE":   return "Turbulência" + (fl.isEmpty() ? "" : " " + fl);
            case "AIRFRAME_ICING": return "Engelamento" + (fl.isEmpty() ? "" : " " + fl);
            case "JETSTREAM":    return "Jetstream " + props.path("windSpeedKt").asText("?") + "kt FL" + flU;
            case "VOLCANO":      return "Vulcão " + props.path("name").asText("?");
            case "TROPICAL_CYCLONE": return "Ciclone " + props.path("name").asText("?");
            default: return tipo;
        }
    }

    private void imprimirRisco(String metar, String taf, boolean temSigmet, Map<String, List<String>> sigwx) {
        String t = (metar + " " + taf).toUpperCase();
        int risco = 0;
        List<String> motivos = new ArrayList<>();

        if (t.matches("(?s).*(?<![A-Z])GR(?![A-Z]).*")) { risco += 3; motivos.add("granizo"); }
        else if (t.contains("TS"))                     { risco += 1; motivos.add("trovoada"); }
        if (t.contains("CB"))                          { risco += 1; motivos.add("CB"); }
        if (temSigmet)                                 { risco += 2; motivos.add("SIGMET ativo"); }
        if (sigwx.containsKey("CLOUD"))                { risco += 1; motivos.add("CB SigWx"); }
        if (sigwx.containsKey("TURBULENCE"))           { risco += 1; motivos.add("turbulência SigWx"); }
        if (sigwx.containsKey("AIRFRAME_ICING"))       { risco += 1; motivos.add("engelamento SigWx"); }

        String nivel = risco >= 5 ? "🔴 ALTO" : risco >= 3 ? "🟡 MODERADO" : "🟢 BAIXO";
        System.out.println("   Nível: " + nivel);
        if (!motivos.isEmpty())
            System.out.println("   Fatores: " + String.join(", ", motivos));
    }

    private String emojiTipo(String tipo) {
        switch (tipo) {
            case "CLOUD": return "⛈️";
            case "TURBULENCE": return "💨";
            case "AIRFRAME_ICING": return "🧊";
            case "JETSTREAM": return "🌬️";
            case "VOLCANO": return "🌋";
            case "TROPICAL_CYCLONE": return "🌀";
            default: return "•";
        }
    }

    // --- helpers de proximidade ---

    private boolean proximoAo(JsonNode geometry, double lat, double lon, double radius) {
        if (geometry == null || geometry.isNull()) return false;
        String type = geometry.path("type").asText();
        JsonNode coords = geometry.get("coordinates");
        if (coords == null) return false;
        try {
            if (type.equals("Point")) {
                return dist(coords.get(1).asDouble(), coords.get(0).asDouble(), lat, lon) <= radius;
            }
            if (type.equals("LineString")) {
                for (JsonNode p : coords)
                    if (dist(p.get(1).asDouble(), p.get(0).asDouble(), lat, lon) <= radius) return true;
            }
            if (type.equals("Polygon")) {
                for (JsonNode ring : coords)
                    for (JsonNode p : ring)
                        if (dist(p.get(1).asDouble(), p.get(0).asDouble(), lat, lon) <= radius) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private double dist(double lat1, double lon1, double lat2, double lon2) {
        return Math.sqrt(Math.pow(lat1 - lat2, 2) + Math.pow(lon1 - lon2, 2));
    }

    // --- chamadas HTTP ---

    private JsonNode getAerodromo(String icao) throws Exception {
        String body = get("/metar_top200_sb");
        JsonNode arr = mapper.readTree(body);
        for (JsonNode n : arr)
            if (icao.equals(n.path("icao").asText())) return n;
        return null;
    }

    private List<JsonNode> getSigmets() throws Exception {
        List<JsonNode> list = new ArrayList<>();
        JsonNode arr = mapper.readTree(get("/redemet_sigmets_json"));
        arr.forEach(list::add);
        return list;
    }

    private List<JsonNode> getSigwxFeatures() throws Exception {
        List<JsonNode> list = new ArrayList<>();
        JsonNode root = mapper.readTree(get("/api/v1/sigwx/current"));
        root.path("features").forEach(list::add);
        return list;
    }

    private String get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE + path)).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }
}
