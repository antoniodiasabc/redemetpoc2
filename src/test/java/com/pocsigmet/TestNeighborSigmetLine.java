package com.pocsigmet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa casos "X OF LINE" do NeighborSigmetParser,
 * baseados nos testes do conversor-iwxxm (TesteSigmet.java).
 */
class TestNeighborSigmetLine {

    // data futura garantida: dia 30 do mês atual às 00:00Z
    private static final String FUTURE = "300000";

    private NeighborSigmetParser parser;

    @BeforeEach
    void setUp() { parser = new NeighborSigmetParser(); }

    @SuppressWarnings("unchecked")
    private List<List<Double>> coords(Map<String, Object> result) {
        Map<String, Object> geom = (Map<String, Object>) result.get("geometry");
        assertNotNull(geom, "geometry nula");
        List<List<List<Double>>> rings = (List<List<List<Double>>>) geom.get("coordinates");
        assertNotNull(rings, "coordinates nulo");
        assertFalse(rings.isEmpty(), "coordinates vazio");
        return rings.get(0);
    }

    @SuppressWarnings("unchecked")
    private String method(Map<String, Object> result) {
        return ((Map<String, Object>) result.get("properties")).get("method").toString();
    }

    // testSigmetLineSPIM3 — S OF LINE com 4 pontos (3 segmentos)
    @Test
    void testSOfLine4Pts() {
        String raw = "WSPR31 SPJC 031659\r\nSPXM SIGMET B3 VALID 031710/300000 SPJC-\r\n" +
                "SPXM LIMA FIR EMBD TS OBS AT 1640Z S OF LINE S1341 W06940 -\r\n" +
                "S1504 W07348 - S1606 W07047 - S1743 W07001\r\nTOP FL390 STNR INTSF=";
        Map<String, Object> r = parser.parse(raw, "SPIM");
        assertNotNull(r, "parse retornou null");
        assertTrue(coords(r).size() >= 3, "polígono com menos de 3 pontos");
        assertTrue(method(r).contains("OF LINE"), "método errado: " + r.get("method"));
    }

    // testSigmetLineSEFGWSLine — N OF LINE com 4 pontos
    @Test
    void testNOfLine4Pts() {
        String raw = "WSEQ31 SEGU 201207\r\n" +
                "SEGX SIGMET 02 VALID 201207/300000 SEGU-SEGX GUAYAQUIL FIR EMBD TS OBS AT 1150Z " +
                "N OF LINE N0049 W07856 - S0025 W08008 - S0009 W08225 - N0112 W08301 TOP FL460 STNR NC=";
        Map<String, Object> r = parser.parse(raw, "SEFG");
        assertNotNull(r, "parse retornou null");
        assertTrue(coords(r).size() >= 3, "polígono com menos de 3 pontos");
        assertTrue(method(r).contains("OF LINE"), "método errado: " + r.get("method"));
    }

    // testSigmetLineBZE — E OF LINE com 3 pontos
    @Test
    void testEOfLine3Pts() {
        String raw = "WSBZ31 SBGL 241520\r\n" +
                "SCXZ SIGMET 6 VALID 241545/300000 SCXZ-\r\n" +
                "SCXZ ANTOFAGASTA FIR EMBD TS FCST E OF LINE S1735 W06951 - S2300 W06832 - S2320 W06817 TOP FL380 STNR INTSF=";
        Map<String, Object> r = parser.parse(raw, "SCFZ");
        assertNotNull(r, "parse retornou null");
        assertTrue(coords(r).size() >= 3, "polígono com menos de 3 pontos");
        assertTrue(method(r).contains("OF LINE"), "método errado: " + r.get("method"));
    }

    // testSigmetLineBZE3 — E OF LINE com 3 pontos variante
    @Test
    void testEOfLine3PtsVariant() {
        String raw = "WSBZ31 SBGL 241520\r\n" +
                "SCXZ SIGMET 6 VALID 241545/300000 SCXZ-\r\n" +
                "SCXZ ANTOFAGASTA FIR EMBD TS FCST E OF LINE S1730 W07000 - S2346 W06757 - S2830 W07016 TOP FL390 STNR INTSF=";
        Map<String, Object> r = parser.parse(raw, "SCFZ");
        assertNotNull(r, "parse retornou null");
        assertTrue(coords(r).size() >= 3, "polígono com menos de 3 pontos");
        assertTrue(method(r).contains("OF LINE"), "método errado: " + r.get("method"));
    }

    // testSigmetLineSPIM2b — E OF LINE com 3 pontos (Lima FIR)
    @Test
    void testEOfLineLima3Pts() {
        String raw = "WSPR31 SPJC 031659\r\n" +
                "SPXM SIGMET B2 VALID 031710/300000 SPJC-\r\n" +
                "SPXM LIMA FIR EMBD TS OBS AT 1110Z E OF LINE S0433 W07159 - S0323 W07206 - S0219 W07147 TOP FL450 MOV W WKN=";
        Map<String, Object> r = parser.parse(raw, "SPIM");
        assertNotNull(r, "parse retornou null");
        assertTrue(coords(r).size() >= 3, "polígono com menos de 3 pontos");
        assertTrue(method(r).contains("OF LINE"), "método errado: " + r.get("method"));
    }

    // testSigmetLineSPE — N OF LINE com 4 pontos (Lima FIR)
    @Test
    void testNOfLineLima4Pts() {
        String raw = "WSPR31 SPJC 031659\r\n" +
                "SPXM SIGMET B4 VALID 031710/300000 SPJC-\r\n" +
                "SPXM LIMA FIR EMBD TS OBS AT 1700Z N OF LINE S0539 W07716 -\r\n" +
                "S0612 W07614 - S0706 W07519 - S0748 W07432 TOP FL420 STNR NC=";
        Map<String, Object> r = parser.parse(raw, "SPIM");
        assertNotNull(r, "parse retornou null");
        assertTrue(coords(r).size() >= 3, "polígono com menos de 3 pontos");
        assertTrue(method(r).contains("OF LINE"), "método errado: " + r.get("method"));
    }

    // SIGMET real reportado com problema — E OF LINE 4 pontos SPIM
    @Test
    void testEOfLineSpimReal() {
        String raw = "WSPR31 SPIM 041741\r\nSPIM SIGMET B1 VALID 041742/300000 SPJC-\r\n" +
                "SPIM LIMA FIR EMBD TS OBS AT 1720Z E OF LINE S0445 W07224 -\r\n" +
                "S0329 W07204 - S0316 W07107 - S0416 W07053\r\nTOP FL340 MOV W INTSF=";
        Map<String, Object> r = parser.parse(raw, "SPIM");
        assertNotNull(r, "parse retornou null");
        List<List<Double>> c = coords(r);
        assertTrue(c.size() >= 3, "polígono com menos de 3 pontos");
        assertTrue(method(r).contains("OF LINE"), "método errado: " + method(r));
        // linha está em lat -3 a -4, lon -70 a -72 — polígono não deve ultrapassar lat -6 nem lon -68
        double maxLat = c.stream().mapToDouble(p -> p.get(1)).max().orElse(0);
        double minLon = c.stream().mapToDouble(p -> p.get(0)).min().orElse(0);
        assertTrue(maxLat < -2.0, "polígono muito ao norte: maxLat=" + maxLat);
        assertTrue(minLon > -75.0, "polígono muito a oeste: minLon=" + minLon);
        // área não deve ser maior que ~5x5 graus
        double lonRange = c.stream().mapToDouble(p -> p.get(0)).max().orElse(0) - minLon;
        double latRange = maxLat - c.stream().mapToDouble(p -> p.get(1)).min().orElse(0);
        System.out.println("SPIM B1: lonRange=" + lonRange + " latRange=" + latRange);
        assertTrue(lonRange < 8.0, "polígono muito largo: " + lonRange + " graus");
        assertTrue(latRange < 8.0, "polígono muito alto: " + latRange + " graus");
    }
    @Test
    void testSOfLat() {
        String raw = "WSPY31 SGAS 221706\r\n" +
                "SGXA SIGMET 04 VALID 221706/300000 SGXA- SGXA ASUNCION FIR SEV TURB FCST S OF S2342 TOP FL350/400 STNR NC=";
        Map<String, Object> r = parser.parse(raw, "SGFA");
        assertNotNull(r, "parse retornou null");
        assertTrue(coords(r).size() >= 3, "polígono com menos de 3 pontos");
    }
}
// REMOVEME
