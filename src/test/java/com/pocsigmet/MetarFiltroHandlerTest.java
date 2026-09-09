package com.pocsigmet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;

public class MetarFiltroHandlerTest {

    private static final String HANDLERS_DIR = "data/config/handlers/";
    private static final String CLASSES_DIR  = "target/test-dynamic-filtro/";

    @BeforeEach
    void setup() {
        new File(CLASSES_DIR).mkdirs();
        RedisMetarCacheService.getMemoryCache().clear();
        RedisMetarCacheService.getMemoryCache().put("metar:SBSP",
            new RedisMetarCacheService.CachedMetarData("IFR", "METAR SBSP 010000Z 00000KT 0800 FG OVC002 05/03 Q1010", "", false));
        RedisMetarCacheService.getMemoryCache().put("metar:SBGR",
            new RedisMetarCacheService.CachedMetarData("IFR", "METAR SBGR 010000Z 28025KT 1500 TSRA BKN010CB 32/18 Q1008", "", false));
        RedisMetarCacheService.getMemoryCache().put("metar:SBCT",
            new RedisMetarCacheService.CachedMetarData("VFR", "METAR SBCT 010000Z 00000KT 9999 FEW020 25/18 Q1015", "", false));
    }

    private Class<?> compile() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK necessário");
        File[] sources = new File(HANDLERS_DIR).listFiles((d, n) -> n.endsWith(".java"));
        String[] args = new String[sources.length + 4];
        args[0] = "-classpath"; args[1] = System.getProperty("java.class.path");
        args[2] = "-d";         args[3] = CLASSES_DIR;
        for (int i = 0; i < sources.length; i++) args[4 + i] = sources[i].getAbsolutePath();
        int r = compiler.run(null, null, null, args);
        assertEquals(0, r, "Compilação deve ter sucesso");
        return new URLClassLoader(
            new java.net.URL[]{new File(CLASSES_DIR).toURI().toURL()},
            getClass().getClassLoader()
        ).loadClass("MetarFiltroHandler");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(Class<?> cls, List<String> filtros, String op) throws Exception {
        Object instance = cls.getDeclaredConstructor().newInstance();
        Method m = cls.getMethod("handleTest", List.class, String.class);
        return (Map<String, Object>) m.invoke(instance, filtros, op);
    }

    @Test
    void compilaHandler() throws Exception {
        assertNotNull(compile());
    }

    @Test
    void filtroNevoeiro() throws Exception {
        var result = invoke(compile(), List.of("nevoeiro"), "or");
        assertEquals(1, result.get("total"));
        assertTrue(result.get("aerodromos").toString().contains("SBSP"));
    }

    @Test
    void filtroTrovoada() throws Exception {
        var result = invoke(compile(), List.of("trovoada"), "or");
        assertEquals(1, result.get("total"));
        assertTrue(result.get("aerodromos").toString().contains("SBGR"));
    }

    @Test
    void filtroTeto600() throws Exception {
        var result = invoke(compile(), List.of("teto600"), "or");
        // SBSP OVC002 = 200ft < 600m(~1968ft) → true
        assertTrue((int) result.get("total") >= 1);
    }

    @Test
    void filtroMultiploAnd() throws Exception {
        // trovoada AND nevoeiro — só SBSP tem FG mas não TS, SBGR tem TS mas não FG
        var result = invoke(compile(), List.of("trovoada", "nevoeiro"), "and");
        assertEquals(0, result.get("total"));
    }

    @Test
    void filtroMultiploOr() throws Exception {
        // trovoada OR nevoeiro — SBSP(FG) + SBGR(TS)
        var result = invoke(compile(), List.of("trovoada", "nevoeiro"), "or");
        assertEquals(2, result.get("total"));
    }

    @Test
    void filtroTempMenorQue10() throws Exception {
        // SBSP temp=05 < 10 → true | SBGR temp=32 → false | SBCT temp=25 → false
        var result = invoke(compile(), List.of("temp<10"), "or");
        assertEquals(1, result.get("total"));
        assertTrue(result.get("aerodromos").toString().contains("SBSP"));
    }

    @Test
    void filtroTempMaiorQue30() throws Exception {
        // SBGR temp=32 > 30 → true
        var result = invoke(compile(), List.of("temp>30"), "or");
        assertEquals(1, result.get("total"));
        assertTrue(result.get("aerodromos").toString().contains("SBGR"));
    }

    @Test
    void filtroTempCombinado() throws Exception {
        // temp>10 AND trovoada → só SBGR (32°C e TS)
        var result = invoke(compile(), List.of("temp>10", "trovoada"), "and");
        assertEquals(1, result.get("total"));
        assertTrue(result.get("aerodromos").toString().contains("SBGR"));
    }

    @Test
    void semFenomenoNaoAparece() throws Exception {
        var result = invoke(compile(), List.of("trovoada"), "or");
        assertFalse(result.get("aerodromos").toString().contains("SBCT"));
    }
}
