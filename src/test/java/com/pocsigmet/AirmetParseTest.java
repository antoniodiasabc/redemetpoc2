package com.pocsigmet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testa o parse dos 6 AIRMETs reais que a API retorna.
 * As strings simulam o conteúdo após split("=") — sem o "=" final.
 * Bug conhecido: último ponto de coordenada é descartado quando "STNR NC"
 * está colado por espaço simples (ex: "S2541 W04939 STNR NC" falha no regex).
 */
public class AirmetParseTest {

    private Object client;
    private Method parseMethod;

    // Exatamente como chegam após response.body().split("=") na aplicação
    private static final String[][] AIRMETS = {
        {"SBCW", "SBCW AIRMET 6 VALID 121015/121130 SBCW - SBCW CURITIBA FIR SFC VIS 0200M FG FCST WI S2541 W04939 - S2541 W04849 - S2507 W04849 - S2507 W04939 - S2541 W04939 STNR NC"},
        {"SBCW", "SBCW AIRMET 7 VALID 121015/121130 SBCW - SBCW CURITIBA FIR SFC VIS 0500M FG FCST WI S2928 W05136 - S2928 W05046 - S2856 W05046 - S2856 W05136 - S2928 W05136 STNR NC"},
        {"SBCW", "SBCW AIRMET 8 VALID 121015/121130 SBCW - SBCW CURITIBA FIR OVC CLD 000/0500FT FCST WI S2928 W05136 - S2928 W05046 - S2856 W05046 - S2856 W05136 - S2928 W05136 STNR NC"},
        {"SBRE", "SBRE AIRMET 5 VALID 120915/121120 SBRE - SBRE RECIFE FIR BKN CLD 100/0600FT FCST WI S1230 W03919 - S1230 W03830 - S1154 W03830 - S1154 W03919 - S1230 W03919 STNR NC"},
        {"SBRE", "SBRE AIRMET 6 VALID 120915/121120 SBRE - SBRE RECIFE FIR SFC VIS 0200M FG FCST WI S1230 W03919 - S1230 W03830 - S1154 W03830 - S1154 W03919 - S1230 W03919 STNR NC"},
        {"SBRE", "SBRE AIRMET 7 VALID 120915/121120 SBRE - SBRE RECIFE FIR SFC VIS 2000M RA FCST WI S1749 W04005 - S1749 W03915 - S1714 W03915 - S1714 W04005 - S1749 W04005 STNR NC"},
    };

    @BeforeEach
    void setup() throws Exception {
        client = new RedemetSigmetClient();
        parseMethod = RedemetSigmetClient.class.getDeclaredMethod("parseAirmetCoords", String.class, String.class);
        parseMethod.setAccessible(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void allSixAirmetsMustBeParsed() throws Exception {
        int parsed = 0;
        for (String[] entry : AIRMETS) {
            Map<String, Object> result = (Map<String, Object>) parseMethod.invoke(client, entry[1], entry[0]);
            assertNotNull(result,
                "AIRMET deveria ser parseado mas retornou null: " + entry[1].substring(0, 40));
            parsed++;
        }
        assertEquals(6, parsed);
    }

    @Test
    @SuppressWarnings("unchecked")
    void polygonMustHaveAllFiveCoordinatePoints() throws Exception {
        // Cada AIRMET tem 5 coords na mensagem, mas o 5º repete o 1º (fechamento explícito).
        // O parse extrai 4 únicos + adiciona fechamento = anel com 5 pontos.
        for (String[] entry : AIRMETS) {
            Map<String, Object> result = (Map<String, Object>) parseMethod.invoke(client, entry[1], entry[0]);
            assertNotNull(result, "null para: " + entry[1].substring(0, 40));

            Map<String, Object> geometry = (Map<String, Object>) result.get("geometry");
            List<List<List<Double>>> coords = (List<List<List<Double>>>) geometry.get("coordinates");
            List<List<Double>> ring = coords.get(0);

            // 4 pontos únicos + 1 fechamento = 5
            assertEquals(5, ring.size(),
                "Polígono com pontos incorretos para AIRMET: " + entry[1].substring(0, 40));
            assertEquals(ring.get(0), ring.get(ring.size() - 1),
                "Polígono não está fechado");
        }
    }
}
