package com.pocsigmet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import javax.tools.ToolProvider;

/**
 * Testa o mecanismo de compilação e execução do handler dinâmico
 * sem subir o contexto Spring.
 */
public class DynamicEndpointRegistryTest {

    private static final String HANDLER_SRC  = "src/test/resources/NuvensContagemHandlerTest.java";
    private static final String CLASSES_DIR  = "target/test-dynamic/";

    @BeforeEach
    void setup() {
        new File(CLASSES_DIR).mkdirs();
        // popular cache com dados de teste
        RedisMetarCacheService.getMemoryCache().clear();
        RedisMetarCacheService.getMemoryCache().put("metar:SBSP",
            new RedisMetarCacheService.CachedMetarData("IFR", "METAR SBSP 010000Z 00000KT 0800 OVC002 18/17 Q1010", "", false));
        RedisMetarCacheService.getMemoryCache().put("metar:SBGR",
            new RedisMetarCacheService.CachedMetarData("IFR", "METAR SBGR 010000Z 00000KT 1500 BKN010 20/18 Q1011", "", false));
        RedisMetarCacheService.getMemoryCache().put("metar:SBCT",
            new RedisMetarCacheService.CachedMetarData("VFR", "METAR SBCT 010000Z 00000KT 9999 FEW020 25/18 Q1013", "", false));
    }

    @Test
    void handlerCompila() {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JavaCompiler deve estar disponível no JDK");

        int result = compiler.run(null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", CLASSES_DIR,
            HANDLER_SRC);

        assertEquals(0, result, "Compilação do handler deve ter sucesso");
        assertTrue(new File(CLASSES_DIR + "NuvensContagemHandlerTest.class").exists());
    }

    @Test
    void handlerRetornaContagemOVC() throws Exception {
        // compilar
        var compiler = ToolProvider.getSystemJavaCompiler();
        compiler.run(null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", CLASSES_DIR, HANDLER_SRC);

        // carregar
        URLClassLoader loader = new URLClassLoader(
            new java.net.URL[]{new File(CLASSES_DIR).toURI().toURL()},
            getClass().getClassLoader());
        Class<?> cls = loader.loadClass("NuvensContagemHandlerTest");
        Object instance = cls.getDeclaredConstructor().newInstance();

        // invocar handle("OVC")
        Method m = cls.getMethod("handleTest", String.class);
        @SuppressWarnings("unchecked")
        var result = (java.util.Map<String, Object>) m.invoke(instance, "OVC");

        assertEquals("OVC", result.get("tipo"));
        assertEquals(1, result.get("total"));
        assertTrue(result.get("aerodromos").toString().contains("SBSP"));

        loader.close();
    }

    @Test
    void handlerRetornaContagemBKN() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        compiler.run(null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", CLASSES_DIR, HANDLER_SRC);

        URLClassLoader loader = new URLClassLoader(
            new java.net.URL[]{new File(CLASSES_DIR).toURI().toURL()},
            getClass().getClassLoader());
        Class<?> cls = loader.loadClass("NuvensContagemHandlerTest");
        Object instance = cls.getDeclaredConstructor().newInstance();

        Method m = cls.getMethod("handleTest", String.class);
        @SuppressWarnings("unchecked")
        var result = (java.util.Map<String, Object>) m.invoke(instance, "BKN");

        assertEquals(1, result.get("total"));
        assertTrue(result.get("aerodromos").toString().contains("SBGR"));

        loader.close();
    }

    @Test
    void handlerIgnoraAerodromoSemFenomeno() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        compiler.run(null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", CLASSES_DIR, HANDLER_SRC);

        URLClassLoader loader = new URLClassLoader(
            new java.net.URL[]{new File(CLASSES_DIR).toURI().toURL()},
            getClass().getClassLoader());
        Class<?> cls = loader.loadClass("NuvensContagemHandlerTest");
        Object instance = cls.getDeclaredConstructor().newInstance();

        Method m = cls.getMethod("handleTest", String.class);
        @SuppressWarnings("unchecked")
        var result = (java.util.Map<String, Object>) m.invoke(instance, "OVC");

        // SBCT tem FEW020, não deve aparecer
        assertFalse(result.get("aerodromos").toString().contains("SBCT"));

        loader.close();
    }
}
