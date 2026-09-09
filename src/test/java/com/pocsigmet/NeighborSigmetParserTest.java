package com.pocsigmet;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de testes do NeighborSigmetParser.
 * Cobre: parsing correto, geometria dentro da FIR, linha oblíqua, linha fora da FIR,
 * número nunca "?", cast seguro, rejeições esperadas.
 */
class NeighborSigmetParserTest {

    private NeighborSigmetParser parser;

    private static String futureValid() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime end = now.plusHours(4);
        return String.format("%02d%02d%02d/%02d%02d%02d",
            now.getDayOfMonth(), now.getHour(), now.getMinute(),
            end.getDayOfMonth(), end.getHour(), end.getMinute());
    }

    private static String expiredValid() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime end = now.minusHours(2);
        return String.format("%02d%02d%02d/%02d%02d%02d",
            now.minusHours(4).getDayOfMonth(), now.minusHours(4).getHour(), now.minusHours(4).getMinute(),
            end.getDayOfMonth(), end.getHour(), end.getMinute());
    }

    @BeforeEach
    void setUp() { parser = new NeighborSigmetParser(); }

    @SuppressWarnings("unchecked")
    private List<List<Double>> coords(Map<String, Object> r) {
        List<List<?>> raw = (List<List<?>>) ((List<?>) ((Map<?, ?>) r.get("geometry")).get("coordinates")).get(0);
        List<List<Double>> result = new ArrayList<>();
        for (List<?> pt : raw) {
            List<Double> d = new ArrayList<>();
            for (Object v : pt) d.add(((Number) v).doubleValue());
            result.add(d);
        }
        return result;
    }

    private void assertParsed(Map<String, Object> r, String fir, String expectedType) {
        assertNotNull(r, "Parser retornou null para " + fir);
        Map<?, ?> props = (Map<?, ?>) r.get("properties");
        assertEquals(fir, props.get("fir"));
        assertEquals(expectedType, props.get("sigmetType"));
        List<List<Double>> c = coords(r);
        assertTrue(c.size() >= 4, "Polígono com menos de 4 pontos: " + c.size());
        double minLon = c.stream().mapToDouble(p -> p.get(0)).min().orElse(0);
        double maxLon = c.stream().mapToDouble(p -> p.get(0)).max().orElse(0);
        assertTrue(maxLon - minLon > 0.01, "Polígono degenerado (lon range < 0.01)");
    }

    /** Verifica que nenhuma coordenada ultrapassa ±85.05 (limite Web Mercator) */
    private void assertMercatorSafe(Map<String, Object> r) {
        List<List<Double>> c = coords(r);
        for (List<Double> pt : c) {
            double lat = pt.get(1);
            assertTrue(lat >= -85.06 && lat <= 85.06,
                "Coordenada fora do limite Mercator: lat=" + lat);
        }
    }

    /** Verifica que o centroide do polígono está do lado correto da linha */
    private void assertCorrectSide(Map<String, Object> r, String direction, double lineVal, boolean isLat) {
        List<List<Double>> c = coords(r);
        double cx = c.stream().mapToDouble(p -> p.get(0)).average().orElse(0);
        double cy = c.stream().mapToDouble(p -> p.get(1)).average().orElse(0);
        switch (direction) {
            case "N": assertTrue(cy >= lineVal - 0.1, "Centroide deveria estar ao N de " + lineVal + ", cy=" + cy); break;
            case "S": assertTrue(cy <= lineVal + 0.1, "Centroide deveria estar ao S de " + lineVal + ", cy=" + cy); break;
            case "E": assertTrue(cx >= lineVal - 0.1, "Centroide deveria estar ao E de " + lineVal + ", cx=" + cx); break;
            case "W": assertTrue(cx <= lineVal + 0.1, "Centroide deveria estar ao W de " + lineVal + ", cx=" + cx); break;
        }
    }

    // ── 1. Formato WI ────────────────────────────────────────────────────────

    @Test void wi_thunderstorm_standard() {
        String v = futureValid();
        String msg = "SKED SIGMET A1 VALID " + v + " SKBO-\n" +
            "SKED BOGOTA FIR EMBD TS OBS AT 1120Z WI N0538 W07901 - N0805 W07655 - N0644 W07250 - N0309 W07628 - N0538 W07901 TOP ABV FL410 MOV NE 05KT WKN=";
        Map<String, Object> r = parser.parse(msg, "SKED");
        assertParsed(r, "SKED", "THUNDERSTORM");
        assertNotEquals("?", ((Map<?, ?>) r.get("properties")).get("sigmetNumber"), "Número não deve ser '?'");
    }

    @Test void wi_volcanic_ash_east_coords() {
        String v = futureValid();
        String msg = "FAJO SIGMET A02 VALID " + v + " FAOR-\n" +
            "FAJO JOHANNESBURG OCEANIC FIR SEV ICE FCST WI\n" +
            "S3202 E00642 - S4335 E01836 - S6044 E00951 - S5647 W00242 -\n" +
            "S5218 W00225 - S4348 E00652 - S3537 E00621=\nFL260/400 MOV E NC=";
        assertParsed(parser.parse(msg, "FAJO"), "FAJO", "ICING");
    }

    @Test void wi_coords_clipped_to_fir() {
        // Coordenadas WI que extrapolam a FIR devem ser clipadas
        String v = futureValid();
        String msg = "SUEO SIGMET 5 VALID " + v + " SUMU-\n" +
            "SUEO URUGUAY FIR SEV TURB OBS AT 1800Z WI S3200 W05800 - S3400 W05600 - S3400 W05200 - S3200 W05200 - S3200 W05800 FL200/350 MOV E NC=";
        Map<String, Object> r = parser.parse(msg, "SUEO");
        assertParsed(r, "SUEO", "TURBULENCE");
        // método deve ser WI
        assertEquals("WI", ((Map<?, ?>) r.get("properties")).get("method"));
    }

    // ── 2. Formato X OF LINE — linha vertical/horizontal ────────────────────

    @Test void e_of_line_vertical_correct_side() {
        // Linha vertical lon=-100, direção E → resultado deve estar a leste de -100
        String v = futureValid();
        String msg = "SCIZ SIGMET 04 VALID " + v + " SCIP-\n" +
            "SCIZ ISLA DE PASCUA FIR SEV TURB FCST E OF LINE S2830 W10000 - S3230 W10000 FL200/300 MOV E NC=";
        Map<String, Object> r = parser.parse(msg, "SCIZ");
        assertParsed(r, "SCIZ", "TURBULENCE");
        assertMercatorSafe(r);
        assertCorrectSide(r, "E", -100.0, false);
        // Nenhuma coordenada deve estar a oeste de -100 (com tolerância)
        List<List<Double>> c = coords(r);
        double minLon = c.stream().mapToDouble(p -> p.get(0)).min().orElse(0);
        assertTrue(minLon >= -100.1, "Polígono não deve estar a oeste da linha, minLon=" + minLon);
    }

    @Test void e_of_line_spim() {
        String v = futureValid();
        String msg = "SPIM SIGMET 8 VALID " + v + " SPJC-\n" +
            "SPIM LIMA FIR EMBD TS OBS AT 1850Z E OF LINE S0512 W07302 -\n" +
            "S0635 W07412 - S0737 W07412 - S0755 W07358\nTOP FL420 MOV S INTSF=";
        Map<String, Object> r = parser.parse(msg, "SPIM");
        assertParsed(r, "SPIM", "THUNDERSTORM");
        double maxLon = coords(r).stream().mapToDouble(p -> p.get(0)).max().orElse(-999);
        assertTrue(maxLon > -70, "E OF LINE deve cobrir lado leste, maxLon=" + maxLon);
    }

    @Test void w_of_line() {
        String v = futureValid();
        String msg = "SPIM SIGMET 5 VALID " + v + " SPJC-\n" +
            "SPIM LIMA FIR SEV TURB OBS AT 1200Z W OF LINE S0500 W07500 - S1000 W07600 TOP FL350 MOV E NC=";
        Map<String, Object> r = parser.parse(msg, "SPIM");
        assertParsed(r, "SPIM", "TURBULENCE");
        assertCorrectSide(r, "W", -75.5, false);
    }

    @Test void n_of_line() {
        String v = futureValid();
        String msg = "SVZM SIGMET 01 VALID " + v + " SVMI-\n" +
            "SVZM MAIQUETIA FIR EMBD TS OBS AT 1000Z N OF LINE N0800 W06500 - N0800 W07000 TOP FL400 MOV E NC=";
        Map<String, Object> r = parser.parse(msg, "SVZM");
        assertParsed(r, "SVZM", "THUNDERSTORM");
        assertCorrectSide(r, "N", 8.0, true);
    }

    @Test void se_of_line_oblique() {
        // Linha oblíqua — testa que o half-plane com vetor perpendicular funciona
        String v = futureValid();
        String msg = "SKED SIGMET C1 VALID " + v + " SKBO-\n" +
            "SKED BOGOTA FIR FRQ TS OBS AT 1445Z SE OF LINE S0024 W07440 - N0522 W06753 TOP FL400 INTSF=";
        Map<String, Object> r = parser.parse(msg, "SKED");
        assertParsed(r, "SKED", "THUNDERSTORM");
        // Centroide deve estar ao SE: lon > -71 e lat < 3
        List<List<Double>> c = coords(r);
        double cx = c.stream().mapToDouble(p -> p.get(0)).average().orElse(0);
        double cy = c.stream().mapToDouble(p -> p.get(1)).average().orElse(0);
        assertTrue(cx > -74 && cy < 4, "Centroide SE esperado, got cx=" + cx + " cy=" + cy);
    }

    // ── 3. Linha fora da FIR (Polygonizer não divide) ───────────────────────

    @Test void line_outside_fir_returns_whole_side() {
        // Linha muito ao norte da SCCZ — toda a FIR está ao sul → retorna FIR inteira ou null
        // Não deve lançar exceção nem retornar polígono vazio
        String v = futureValid();
        String msg = "SCCZ SIGMET 01 VALID " + v + " SCIP-\n" +
            "SCCZ PUNTA ARENAS FIR SEV TURB OBS AT 1000Z S OF LINE S4700 W07000 - S4700 W08000 FL260/400 MOV E NC=";
        // Não deve lançar exceção
        Map<String, Object> r = parser.parse(msg, "SCCZ");
        // Pode retornar null (linha fora) ou polígono válido — nunca deve explodir
        if (r != null) {
            List<List<Double>> c = coords(r);
            assertTrue(c.size() >= 4, "Se retornar, deve ter >= 4 pontos");
            assertMercatorSafe(r);
        }
    }

    // ── 4. Formato X OF lat/lon ──────────────────────────────────────────────

    @Test void n_of_lat() {
        String v = futureValid();
        String msg = "SVZM SIGMET 02 VALID " + v + " SVMI-\n" +
            "SVZM MAIQUETIA FIR SEV ICE OBS AT 1000Z N OF N0500 FL200/350 MOV E NC=";
        Map<String, Object> r = parser.parse(msg, "SVZM");
        assertParsed(r, "SVZM", "ICING");
        assertCorrectSide(r, "N", 5.0, true);
    }

    @Test void s_of_lat() {
        String v = futureValid();
        String msg = "SCCZ SIGMET 01 VALID " + v + " SCIP-\n" +
            "SCCZ PUNTA ARENAS FIR SEV TURB OBS AT 1000Z S OF S5000 FL260/400 MOV E NC=";
        Map<String, Object> r = parser.parse(msg, "SCCZ");
        assertParsed(r, "SCCZ", "TURBULENCE");
        assertCorrectSide(r, "S", -50.0, true);
        assertMercatorSafe(r);
    }

    // ── 5. Limite Mercator (bug SCIZ lat=-90) ────────────────────────────────

    @Test void sciz_mercator_safe() {
        // SCIZ tem lat=-90 no JSON → após correção deve estar clampado a -85.05
        String v = futureValid();
        String msg = "SCIZ SIGMET 04 VALID " + v + " SCIP-\n" +
            "SCIZ ISLA DE PASCUA FIR SEV TURB FCST E OF LINE S2830 W10000 - S3230 W10000 FL200/300 MOV E NC=";
        Map<String, Object> r = parser.parse(msg, "SCIZ");
        assertNotNull(r);
        assertMercatorSafe(r);
    }

    @Test void sccz_mercator_safe() {
        String v = futureValid();
        String msg = "SCCZ SIGMET 02 VALID " + v + " SCIP-\n" +
            "SCCZ PUNTA ARENAS FIR EMBD TS OBS AT 1000Z ENTIRE FIR TOP FL400 MOV E NC=";
        Map<String, Object> r = parser.parse(msg, "SCCZ");
        assertNotNull(r);
        assertMercatorSafe(r);
    }

    // ── 6. Número SIGMET nunca deve ser "?" ──────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"SPIM", "SVZM", "SKED", "SEFG", "SARR", "SUEO", "SCIZ", "TTZP", "FAJO"})
    void sigmet_number_never_question_mark(String fir) {
        String v = futureValid();
        String msg = fir + " SIGMET A1 VALID " + v + " XXXX-\n" +
            fir + " TEST FIR EMBD TS OBS AT 1000Z WI N0100 W07500 - N0300 W07300 - N0200 W07100 - N0100 W07500 TOP FL350 MOV N NC=";
        Map<String, Object> r = parser.parse(msg, fir);
        if (r != null) {
            assertNotEquals("?", ((Map<?, ?>) r.get("properties")).get("sigmetNumber"),
                "Número SIGMET não deve ser '?' para FIR " + fir);
        }
    }

    // ── 7. Tipos de fenômeno ─────────────────────────────────────────────────

    @Test void type_icing() {
        String v = futureValid();
        String msg = "SARR SIGMET 5 VALID " + v + " SARE-\n" +
            "SARR ARGENTINA FIR SEV ICE OBS AT 1800Z WI S3000 W06000 - S3500 W05500 - S3500 W05000 - S3000 W05000 - S3000 W06000 FL200/350 MOV E NC=";
        assertParsed(parser.parse(msg, "SARR"), "SARR", "ICING");
    }

    @Test void type_turbulence() {
        String v = futureValid();
        String msg = "SUEO SIGMET 5 VALID " + v + " SUMU-\n" +
            "SUEO URUGUAY FIR SEV TURB OBS AT 1800Z WI S3200 W05800 - S3400 W05600 - S3400 W05200 - S3200 W05200 - S3200 W05800 FL200/350 MOV E NC=";
        assertParsed(parser.parse(msg, "SUEO"), "SUEO", "TURBULENCE");
    }

    @Test void type_mountain_wave() {
        // SAME consulta REDEMET mas texto usa SAMF (ACC code)
        String v = futureValid();
        String msg = "WSAG31 SAME 281459\n" +
            "SAMF SIGMET 3 VALID " + v + " SAME-\n" +
            "SAMF MENDOZA FIR SEV MTW OBS AT 1459Z\n" +
            "WI S3600 W07012 - S3727 W06828 - S3757 W06944 - S3824 W07052 - S3734\n" +
            "W07103 - S3703 W07104 - S3639 W07058 - S3600 W07012 FL040/180 STNR NC=";
        Map<String, Object> r = parser.parse(msg, "SAME");
        assertParsed(r, "SAME", "MOUNTAIN_WAVE");
        assertNotEquals("?", ((Map<?, ?>) r.get("properties")).get("sigmetNumber"));
    }

    @Test void type_volcanic_ash() {
        String v = futureValid();
        String msg = "SEFG SIGMET 3 VALID " + v + " SEGU-\n" +
            "SEFG GUAYAQUIL FIR VA ERUPTION MT REVENTADOR PSN S0004 W07739\n" +
            "VA CLD OBS AT 1720Z WI N0004 W07758 - S0004 W07739 - S0005 W07740 -\n" +
            "S0004 W07758 - N0004 W07758 SFC/FL140 MOV W 15KT=";
        assertParsed(parser.parse(msg, "SEFG"), "SEFG", "VOLCANIC_ASH");
    }

    // ── 8. Variações de formato ──────────────────────────────────────────────

    @Test void valid_with_z_suffix() {
        String v = futureValid().replace("/", "Z/") + "Z";
        String msg = "FAJO SIGMET A03 VALID " + v + " FAOR-\n" +
            "FAJO JOHANNESBURG OCEANIC FIR SEV TURB FCST WI S3000 E00500 - S4000 E01000 - S4000 E01500 - S3000 E01500 - S3000 E00500\nFL260/400 MOV E NC=";
        assertParsed(parser.parse(msg, "FAJO"), "FAJO", "TURBULENCE");
    }

    @Test void alphanumeric_sigmet_number() {
        String v = futureValid();
        String msg = "TTZP SIGMET A1 VALID " + v + " TTPP-\n" +
            "TTZP PIARCO FIR EMBD TS OBS AT 1115Z WI N1000 W06100 - N1200 W06000 - N1200 W05800 - N1000 W05800 - N1000 W06100 TOP FL400 MOV W NC=";
        Map<String, Object> r = parser.parse(msg, "TTZP");
        assertParsed(r, "TTZP", "THUNDERSTORM");
        assertNotEquals("?", ((Map<?, ?>) r.get("properties")).get("sigmetNumber"));
    }

    @Test void multiline_coords() {
        String v = futureValid();
        String msg = "SEFG SIGMET 3 VALID " + v + " SEGU-\n" +
            "SEFG GUAYAQUIL FIR VA ERUPTION MT REVENTADOR PSN S0004 W07739\n" +
            "VA CLD OBS AT 1720Z WI N0004 W07758 - S0004 W07739 - S0005 W07740 - \n" +
            "S0004 W07758 - N0004 W07758 SFC/FL140 MOV W 15KT=";
        assertParsed(parser.parse(msg, "SEFG"), "SEFG", "VOLCANIC_ASH");
    }

    @Test void entire_fir() {
        String v = futureValid();
        String msg = "SCIZ SIGMET 01 VALID " + v + " SCIP-\n" +
            "SCIZ ISLA DE PASCUA FIR EMBD TS OBS AT 1000Z ENTIRE FIR TOP FL400 MOV E NC=";
        Map<String, Object> r = parser.parse(msg, "SCIZ");
        assertParsed(r, "SCIZ", "THUNDERSTORM");
        assertMercatorSafe(r);
    }

    // ── 10. GOOO — casos reais que causaram bugs ─────────────────────────────

    /** B03: linha completamente fora da FIR → deve retornar a FIR inteira clipada */
    @Test void gooo_b03_line_outside_fir_returns_whole_fir() {
        String v = futureValid();
        String msg = "WSSG31 GOOY 021034\n" +
            "GOOO SIGMET B03 VALID " + v + " GOBD-\n" +
            "GOOO DAKAR OCEANIC FIR/UIR EMBD TS OBS AT 1030Z\n" +
            "W OF LINE N1325 W03720 - N1435 W03435 - N1335 W03235 -\n" +
            "          N1110 W03500 - N0720 W03305\n" +
            "TOP FL450 MOV W 12KT NC=";
        Map<String, Object> r = parser.parse(msg, "GOOO");
        assertNotNull(r, "B03 linha fora da FIR deve retornar FIR inteira, não null");
        List<List<Double>> c = coords(r);
        assertTrue(c.size() >= 4);
        // Nenhuma coordenada deve estar dentro da FIR SBAO (lon > -10, lat < 7)
        for (List<Double> pt : c) {
            double lon = pt.get(0), lat = pt.get(1);
            assertFalse(lon > -10 && lat < 7 && lat > -40,
                "Coordenada invade SBAO: lon=" + lon + " lat=" + lat);
        }
        // Deve estar na região da GOOO (Atlântico, lon < -9)
        double maxLon = c.stream().mapToDouble(p -> p.get(0)).max().orElse(0);
        assertTrue(maxLon <= -9.0 + 0.1, "Polígono não deve ultrapassar borda leste da GOOO, maxLon=" + maxLon);
    }

    /** Nenhum SIGMET da GOOO deve invadir a FIR SBAO */
    @Test void gooo_never_invades_sbao() {
        String v = futureValid();
        // WI com coords dentro da GOOO
        String msg = "GOOO SIGMET B04 VALID " + v + " GOBD-\n" +
            "GOOO DAKAR OCEANIC FIR/UIR EMBD TS OBS AT 1045Z\n" +
            "E OF LINE N1500 W02500 - N0500 W02000\n" +
            "TOP FL450 MOV E 10KT NC=";
        Map<String, Object> r = parser.parse(msg, "GOOO");
        if (r != null) {
            for (List<Double> pt : coords(r)) {
                double lon = pt.get(0), lat = pt.get(1);
                // SBAO está aproximadamente a leste de W010
                assertFalse(lon > -8.9, "Coordenada invade SBAO: lon=" + lon);
            }
        }
    }

    /** Polígono da GOOO nunca deve ter coordenadas no Brasil */
    @Test void gooo_no_coords_in_brazil() {
        String v = futureValid();
        String msg = "GOOO SIGMET A01 VALID " + v + " GOBD-\n" +
            "GOOO DAKAR OCEANIC FIR/UIR EMBD TS OBS AT 1000Z ENTIRE FIR TOP FL450 MOV W NC=";
        Map<String, Object> r = parser.parse(msg, "GOOO");
        assertNotNull(r);
        for (List<Double> pt : coords(r)) {
            double lon = pt.get(0), lat = pt.get(1);
            // Brasil está aproximadamente entre lon -35 a -73, lat -35 a 5
            assertFalse(lon < -35 && lon > -73 && lat > -35 && lat < 5,
                "Coordenada dentro do Brasil: lon=" + lon + " lat=" + lat);
        }
    }

    @Test void reject_expired() {
        String v = expiredValid();
        String msg = "SKED SIGMET A1 VALID " + v + " SKBO-\n" +
            "SKED BOGOTA FIR EMBD TS OBS AT 1120Z WI N0538 W07901 - N0805 W07655 - N0644 W07250 - N0309 W07628 - N0538 W07901 TOP FL410 MOV NE WKN=";
        assertNull(parser.parse(msg, "SKED"), "SIGMET expirado deve retornar null");
    }

    @Test void reject_no_valid() {
        String msg = "SKED SIGMET A1 SKBO-\nSKED BOGOTA FIR EMBD TS WI N0538 W07901 - N0538 W07901 TOP FL410=";
        assertNull(parser.parse(msg, "SKED"), "SIGMET sem VALID deve retornar null");
    }

    @Test void reject_no_coords() {
        String v = futureValid();
        String msg = "SKED SIGMET A1 VALID " + v + " SKBO-\nSKED BOGOTA FIR EMBD TS OBS AT 1120Z TOP FL410 MOV NE WKN=";
        assertNull(parser.parse(msg, "SKED"), "SIGMET sem coordenadas deve retornar null");
    }

    // ── 11. Portados do conversor-iwxxm (TesteSigmet.java) ───────────────────

    /** testSigmetLinePG: SGFA E OF LINE 2 pontos oblíquos — lado leste deve ter lon > -60 */
    @Test void sgfa_e_of_line_2pts_oblique() {
        String v = futureValid();
        String msg = "SGXA SIGMET 4 VALID " + v + " SGAS-\n" +
            "SGFA ASUNCION FIR EMBD TS OBS AT 1750Z E OF\n" +
            "LINE S1923 W06002 - S2723 W05549 TOP FL380 STNR NC=";
        Map<String, Object> r = parser.parse(msg, "SGFA");
        assertParsed(r, "SGFA", "THUNDERSTORM");
        // lado leste da linha: lon > -60.5
        double minLon = coords(r).stream().mapToDouble(p -> p.get(0)).min().orElse(-999);
        double maxLon = coords(r).stream().mapToDouble(p -> p.get(0)).max().orElse(-999);
        assertTrue(maxLon > -59.0, "E OF LINE deve cobrir lado leste, maxLon=" + maxLon);
        assertTrue(minLon > -62.0, "E OF LINE não deve invadir lado oeste, minLon=" + minLon);
    }

    /** testSigmetLine5: SGFA S OF LINE 6 pontos (5 segmentos) — centroide deve estar ao sul */
    @Test void sgfa_s_of_line_6pts_multisegment() {
        String v = futureValid();
        String msg = "SGFA SIGMET 03 VALID " + v + " SGAS-\n" +
            "SGFA ASUNCION FIR EMBD TS OBS AT 0650Z S OF LINE S2720 W05601 -\n" +
            "S2531 W05734 - S2104 W06217 - S2006 W05849 - S2225 W05547 - S2521\n" +
            "W05433 FL290/390 MOV ESE 05KT NC=";
        Map<String, Object> r = parser.parse(msg, "SGFA");
        assertParsed(r, "SGFA", "THUNDERSTORM");
        // centroide deve estar ao sul da linha (lat < -21)
        double cy = coords(r).stream().mapToDouble(p -> p.get(1)).average().orElse(0);
        assertTrue(cy < -20.0, "S OF LINE: centroide deve estar ao sul, cy=" + cy);
    }

    /** SOOO S OF LINE 4 pontos — centroide ao sul */
    @Test void sooo_s_of_line_4pts() {
        String v = futureValid();
        String msg = "SOOO SIGMET 5 VALID " + v + " TFFF-\n" +
            "SOOO CAYENNE FIR/UIR EMBD TS OBS AT 1000Z S OF LINE\n" +
            "N0545 W05200 - N0545 W05045 - N0400 W04800 - N0300 W04500\n" +
            "TOP FL450 STNR NC=";
        Map<String, Object> r = parser.parse(msg, "SOOO");
        assertParsed(r, "SOOO", "THUNDERSTORM");
        double cy = coords(r).stream().mapToDouble(p -> p.get(1)).average().orElse(0);
        assertTrue(cy < 5.5, "S OF LINE: centroide deve estar ao sul, cy=" + cy);
    }

    /** SPIM E OF LINE 4 pontos (caso real SPIM 9) — maxLon deve estar próximo da borda leste da FIR */
    @Test void spim_e_of_line_4pts_real() {
        String v = futureValid();
        String msg = "SPIM SIGMET 9 VALID " + v + " SPJC-\n" +
            "SPIM LIMA FIR EMBD TS OBS AT 1850Z E OF LINE\n" +
            "S0512 W07302 - S0635 W07412 - S0737 W07412 - S0755 W07358\n" +
            "TOP FL420 MOV S INTSF=";
        Map<String, Object> r = parser.parse(msg, "SPIM");
        assertParsed(r, "SPIM", "THUNDERSTORM");
        double maxLon = coords(r).stream().mapToDouble(p -> p.get(0)).max().orElse(-999);
        double minLon = coords(r).stream().mapToDouble(p -> p.get(0)).min().orElse(-999);
        assertTrue(maxLon > -70.0, "E OF LINE deve cobrir lado leste, maxLon=" + maxLon);
        // lado leste da SPIM vai até ~W089 — não deve ultrapassar a linha (~W074)
        assertTrue(minLon < -73.0, "E OF LINE deve incluir pontos a oeste da linha, minLon=" + minLon);
    }

    /** SKED W OF LINE 3 pontos oblíquos — centroide ao oeste */
    @Test void sked_w_of_line_3pts() {
        String v = futureValid();
        String msg = "SKED SIGMET 7 VALID " + v + " SKBO-\n" +
            "SKED BOGOTA FIR EMBD TS OBS AT 1000Z W OF LINE\n" +
            "N0800 W07200 - N0500 W07400 - N0200 W07300\n" +
            "TOP FL400 MOV E NC=";
        Map<String, Object> r = parser.parse(msg, "SKED");
        assertParsed(r, "SKED", "THUNDERSTORM");
        double cx = coords(r).stream().mapToDouble(p -> p.get(0)).average().orElse(0);
        assertTrue(cx < -72.0, "W OF LINE: centroide deve estar ao oeste, cx=" + cx);
    }
}
