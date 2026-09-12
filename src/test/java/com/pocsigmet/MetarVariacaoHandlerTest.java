package com.pocsigmet;

import com.pocsigmet.mongo.MetarDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MetarVariacaoHandlerTest {

    private static final String HANDLERS_DIR = "data/config/handlers/";
    private static final String CLASSES_DIR  = "target/test-dynamic-variacao/";

    private MongoTemplate mongo;
    private Class<?> handlerClass;

    @BeforeEach
    void setup() throws Exception {
        new File(CLASSES_DIR).mkdirs();
        mongo = mock(MongoTemplate.class);
        handlerClass = compile();
    }

    private Class<?> compile() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK necessário");
        File[] sources = new File(HANDLERS_DIR).listFiles((d, n) -> n.endsWith(".java"));
        String[] args = new String[sources.length + 4];
        args[0] = "-classpath"; args[1] = System.getProperty("java.class.path");
        args[2] = "-d";         args[3] = CLASSES_DIR;
        for (int i = 0; i < sources.length; i++) args[4 + i] = sources[i].getAbsolutePath();
        assertEquals(0, compiler.run(null, null, null, args), "Compilação deve ter sucesso");
        return new URLClassLoader(
            new java.net.URL[]{new File(CLASSES_DIR).toURI().toURL()},
            getClass().getClassLoader()
        ).loadClass("MetarVariacaoHandler");
    }

    private MetarDocument doc(String icao, String text, long ts) {
        return new MetarDocument(icao, "VFR", text, "", false, ts);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(double limiar, int sinal) throws Exception {
        Object instance = handlerClass.getDeclaredConstructor().newInstance();
        Field f = handlerClass.getField("mongoTemplate");
        f.set(instance, mongo);
        Method m = handlerClass.getMethod("handleTest", double.class, int.class);
        return (Map<String, Object>) m.invoke(instance, limiar, sinal);
    }

    @Test
    void compilaHandler() {
        assertNotNull(handlerClass);
    }

    @Test
    void detectaVariacaoTemperatura() throws Exception {
        when(mongo.findDistinct(any(Query.class), eq("icao"), eq(MetarDocument.class), eq(String.class)))
            .thenReturn(List.of("SBSP"));
        when(mongo.find(any(Query.class), eq(MetarDocument.class)))
            .thenReturn(List.of(
                doc("SBSP", "METAR SBSP 100200Z 06004KT CAVOK 30/18 Q1020", 2000L),
                doc("SBSP", "METAR SBSP 100100Z 06004KT CAVOK 20/18 Q1020", 1000L)
            ));

        var result = invoke(10.0, 0);
        assertEquals(1, result.get("total"));
        List<Map<String, Object>> aerodromos = (List<Map<String, Object>>) result.get("aerodromos");
        Map<String, Object> variacoes = (Map<String, Object>) aerodromos.get(0).get("variacoes");
        assertTrue(variacoes.containsKey("temp"));
        Map<String, Object> temp = (Map<String, Object>) variacoes.get("temp");
        assertEquals(30.0, temp.get("atual"));
        assertEquals(20.0, temp.get("anterior"));
        assertEquals(50.0, temp.get("variacao_pct"));
    }

    @Test
    void ignoraAerodromoComUmaSoMensagem() throws Exception {
        when(mongo.findDistinct(any(Query.class), eq("icao"), eq(MetarDocument.class), eq(String.class)))
            .thenReturn(List.of("SBGR"));
        when(mongo.find(any(Query.class), eq(MetarDocument.class)))
            .thenReturn(List.of(doc("SBGR", "METAR SBGR 100200Z 07008KT 2000 BR OVC002 18/18 Q1020", 1000L)));

        var result = invoke(10.0, 0);
        assertEquals(0, result.get("total"));
    }

    @Test
    void ignoraVariacaoAbaixoDoLimiar() throws Exception {
        when(mongo.findDistinct(any(Query.class), eq("icao"), eq(MetarDocument.class), eq(String.class)))
            .thenReturn(List.of("SBCT"));
        when(mongo.find(any(Query.class), eq(MetarDocument.class)))
            .thenReturn(List.of(
                doc("SBCT", "METAR SBCT 100200Z 06004KT CAVOK 21/18 Q1020", 2000L),
                doc("SBCT", "METAR SBCT 100100Z 06004KT CAVOK 20/18 Q1020", 1000L)
            ));

        var result = invoke(10.0, 0);
        assertEquals(0, result.get("total"));
    }

    @Test
    void retornaAmbasMensagens() throws Exception {
        when(mongo.findDistinct(any(Query.class), eq("icao"), eq(MetarDocument.class), eq(String.class)))
            .thenReturn(List.of("SBBR"));
        when(mongo.find(any(Query.class), eq(MetarDocument.class)))
            .thenReturn(List.of(
                doc("SBBR", "METAR SBBR 100200Z 06010KT CAVOK 27/16 Q1022", 2000L),
                doc("SBBR", "METAR SBBR 100100Z VRB02KT CAVOK 24/16 Q1021", 1000L)
            ));

        var result = invoke(10.0, 0);
        List<Map<String, Object>> aerodromos = (List<Map<String, Object>>) result.get("aerodromos");
        assertFalse(aerodromos.isEmpty());
        assertTrue(aerodromos.get(0).containsKey("msg_anterior"));
        assertTrue(aerodromos.get(0).containsKey("msg_atual"));
    }

    @Test
    void sinalPositivoSoPegaQuemSubiu() throws Exception {
        // temp subiu 20→30 (+50%), vento caiu 10→4 (-60%)
        when(mongo.findDistinct(any(Query.class), eq("icao"), eq(MetarDocument.class), eq(String.class)))
            .thenReturn(List.of("SBSP"));
        when(mongo.find(any(Query.class), eq(MetarDocument.class)))
            .thenReturn(List.of(
                doc("SBSP", "METAR SBSP 100200Z 04004KT CAVOK 30/18 Q1020", 2000L),
                doc("SBSP", "METAR SBSP 100100Z 04010KT CAVOK 20/18 Q1020", 1000L)
            ));

        var result = invoke(40.0, 1);
        List<Map<String, Object>> aerodromos = (List<Map<String, Object>>) result.get("aerodromos");
        Map<String, Object> variacoes = (Map<String, Object>) aerodromos.get(0).get("variacoes");
        assertTrue(variacoes.containsKey("temp"));   // subiu 50% ✅
        assertFalse(variacoes.containsKey("vento")); // caiu 60% — excluído pelo sinal +
    }

    @Test
    void sinalNegativoSoPegaQuemCaiu() throws Exception {
        // temp subiu 20→30 (+50%), vento caiu 10→4 (-60%)
        when(mongo.findDistinct(any(Query.class), eq("icao"), eq(MetarDocument.class), eq(String.class)))
            .thenReturn(List.of("SBSP"));
        when(mongo.find(any(Query.class), eq(MetarDocument.class)))
            .thenReturn(List.of(
                doc("SBSP", "METAR SBSP 100200Z 04004KT CAVOK 30/18 Q1020", 2000L),
                doc("SBSP", "METAR SBSP 100100Z 04010KT CAVOK 20/18 Q1020", 1000L)
            ));

        var result = invoke(40.0, -1);
        List<Map<String, Object>> aerodromos = (List<Map<String, Object>>) result.get("aerodromos");
        Map<String, Object> variacoes = (Map<String, Object>) aerodromos.get(0).get("variacoes");
        assertFalse(variacoes.containsKey("temp"));  // subiu — excluído pelo sinal -
        assertTrue(variacoes.containsKey("vento"));  // caiu 60% ✅
    }
}
