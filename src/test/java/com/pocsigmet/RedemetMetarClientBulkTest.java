package com.pocsigmet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RedemetMetarClientBulkTest {

    // Simula resposta da REDEMET com 3 ICAOs misturados, cada um com 2 mensagens
    private static final String BULK_RESPONSE =
        "METAR SBGR 181200Z 09010KT 9999 FEW020 25/18 Q1018=" +
        "METAR SBPA 181155Z 18008KT 8000 SCT015 BKN025 18/15 Q1012=" +
        "SPECI SBPA 181215Z 18015KT 3000 RA BKN008 OVC015 16/15 Q1010=" +
        "METAR SBSP 181200Z 00000KT 6000 BKN010 22/19 Q1016=" +
        "METAR SBGR 181100Z 09008KT 9999 SCT025 24/17 Q1018=";

    @Test
    void extractLatest_retornaMaisRecentePorTimestamp() {
        // SBGR: 181200Z > 181100Z → deve retornar 181200Z
        String result = RedemetMetarClient.extractLatest(BULK_RESPONSE, "SBGR");
        assertTrue(result.contains("181200Z"), "SBGR deve retornar METAR 181200Z, got: " + result);
    }

    @Test
    void extractLatest_retornaSpeciQuandoMaisRecente() {
        // SBPA: SPECI 181215Z > METAR 181155Z → deve retornar SPECI
        String result = RedemetMetarClient.extractLatest(BULK_RESPONSE, "SBPA");
        assertTrue(result.contains("SPECI"), "SBPA deve retornar SPECI, got: " + result);
        assertTrue(result.contains("181215Z"), "SBPA deve retornar 181215Z, got: " + result);
    }

    @Test
    void extractLatest_retornaNaoDisponivel_quandoIcaoAusente() {
        String result = RedemetMetarClient.extractLatest(BULK_RESPONSE, "SBBR");
        assertEquals("METAR/SPECI não disponível", result);
    }

    @Test
    void extractLatest_retornaNaoDisponivel_quandoBodyVazio() {
        assertEquals("METAR/SPECI não disponível", RedemetMetarClient.extractLatest("", "SBSP"));
        assertEquals("METAR/SPECI não disponível", RedemetMetarClient.extractLatest(null, "SBSP"));
    }

    @Test
    void extractLatest_retornaMensagem_quandoUmaSoMensagem() {
        String result = RedemetMetarClient.extractLatest(BULK_RESPONSE, "SBSP");
        assertTrue(result.contains("SBSP") && result.contains("181200Z"), "got: " + result);
    }
}
