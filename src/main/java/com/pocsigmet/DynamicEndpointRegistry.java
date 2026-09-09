package com.pocsigmet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registra endpoints dinamicamente a partir de /app/data/endpoints.json.
 * Cada entrada aponta para um .java em /app/data/handlers/ que é compilado
 * em runtime e registrado no Spring sem restart.
 */
@Component
public class DynamicEndpointRegistry {

    private static final Logger log = LoggerFactory.getLogger(DynamicEndpointRegistry.class);
    private static final String ENDPOINTS_FILE = "/app/data/config/endpoints.json";
    private static final String HANDLERS_DIR   = "/app/data/config/handlers/";
    private static final String CLASSES_DIR    = "/tmp/dynamic-handlers/";
    private static final ObjectMapper mapper   = new ObjectMapper();

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;
    @Autowired private ApplicationContext ctx;

    // path -> classloader ativo (para fechar no reload)
    private final Map<String, URLClassLoader> loaders = new ConcurrentHashMap<>();
    // path -> instância registrada
    private final Map<String, Object> instances = new ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        new File(CLASSES_DIR).mkdirs();
        extractClasspath();
        loadAll();
        startWatcher();
    }

    /** Extrai o fat-jar para /tmp/app-exploded/ para que o javac consiga resolver as classes */
    private void extractClasspath() {
        File exploded = new File("/tmp/app-exploded");
        if (exploded.exists()) return; // já extraído
        exploded.mkdirs();
        try {
            Process p = new ProcessBuilder("jar", "xf", "/app/app.jar")
                .directory(exploded)
                .redirectErrorStream(true)
                .start();
            p.waitFor();
            log.info("✅ fat-jar extraído para compilação dinâmica");
        } catch (Exception e) {
            log.warn("Extração do fat-jar falhou: {}", e.getMessage());
        }
    }

    private String buildClasspath() {
        // BOOT-INF/classes + todos os JARs em BOOT-INF/lib + handlers compilados
        StringBuilder cp = new StringBuilder("/tmp/app-exploded/BOOT-INF/classes");
        cp.append(File.pathSeparator).append(CLASSES_DIR);
        File lib = new File("/tmp/app-exploded/BOOT-INF/lib");
        if (lib.exists()) {
            for (File jar : lib.listFiles((d, n) -> n.endsWith(".jar"))) {
                cp.append(File.pathSeparator).append(jar.getAbsolutePath());
            }
        }
        return cp.toString();
    }

    private void compileAllHandlers() {
        File dir = new File(HANDLERS_DIR);
        if (!dir.exists()) return;
        File[] sources = dir.listFiles((d, n) -> n.endsWith(".java"));
        if (sources == null || sources.length == 0) return;
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) return;
        String cp = buildClasspath();
        String[] args = new String[sources.length + 4];
        args[0] = "-classpath"; args[1] = cp;
        args[2] = "-d"; args[3] = CLASSES_DIR;
        for (int i = 0; i < sources.length; i++) args[4 + i] = sources[i].getAbsolutePath();
        int result = compiler.run(null, null, null, args);
        log.info("Compilação de handlers: {}", result == 0 ? "OK" : "ERRO");
    }

    private void loadAll() {
        compileAllHandlers();

        // Modo 1 — endpoints.json (backward compatible)
        File f = new File(ENDPOINTS_FILE);
        if (f.exists()) {
            try {
                for (JsonNode node : mapper.readTree(f)) register(node);
            } catch (Exception e) {
                log.error("Erro ao carregar endpoints.json: {}", e.getMessage());
            }
        }

        // Modo 2 — @DynamicEndpoint nas classes compiladas
        File classesDir = new File(CLASSES_DIR);
        if (!classesDir.exists()) return;
        URLClassLoader scanner = null;
        try {
            scanner = new URLClassLoader(
                new java.net.URL[]{classesDir.toURI().toURL()},
                getClass().getClassLoader());
            for (File cls : classesDir.listFiles((d, n) -> n.endsWith(".class"))) {
                String className = cls.getName().replace(".class", "");
                try {
                    Class<?> c = scanner.loadClass(className);
                    DynamicEndpoint ann = c.getAnnotation(DynamicEndpoint.class);
                    if (ann == null) continue;
                    registerFromAnnotation(c, ann);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.error("Erro ao escanear @DynamicEndpoint: {}", e.getMessage());
        } finally {
            if (scanner != null) try { scanner.close(); } catch (Exception ignored) {}
        }
    }

    private void registerFromAnnotation(Class<?> cls, DynamicEndpoint ann) {
        String path = ann.path();
        try {
            URLClassLoader old = loaders.get(path);
            if (old != null) try { old.close(); } catch (Exception ignored) {}

            URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{new File(CLASSES_DIR).toURI().toURL()},
                getClass().getClassLoader());
            loaders.put(path, loader);

            Class<?> reloaded = loader.loadClass(cls.getSimpleName());
            Object instance = reloaded.getDeclaredConstructor().newInstance();

            for (Field field : reloaded.getFields()) {
                try { field.set(instance, ctx.getBean(field.getType())); } catch (Exception ignored) {}
            }

            instances.put(path, instance);
            unregister(path);

            Method handleMethod = reloaded.getMethod("handle", HttpServletRequest.class, HttpServletResponse.class);
            RequestMappingInfo.BuilderConfiguration opts = new RequestMappingInfo.BuilderConfiguration();
            if (handlerMapping.getPatternParser() != null)
                opts.setPatternParser(handlerMapping.getPatternParser());
            RequestMappingInfo mapping = RequestMappingInfo
                .paths(path)
                .methods(RequestMethod.valueOf(ann.method()))
                .options(opts)
                .build();

            handlerMapping.registerMapping(mapping, instance, handleMethod);
            log.info("✅ Endpoint @DynamicEndpoint registrado: {} {}", ann.method(), path);
        } catch (Exception e) {
            log.error("Erro ao registrar @DynamicEndpoint {}: {}", path, e.getMessage());
        }
    }

    private void register(JsonNode node) {
        String path        = node.get("path").asText();
        String handlerFile = node.get("handlerClass").asText();
        String methodName  = node.get("method").asText();

        try {
            File src = new File(handlerFile);
            if (!src.exists()) { log.warn("Handler não encontrado: {}", handlerFile); return; }

            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) { log.error("JavaCompiler indisponível — use JDK, não JRE"); return; }
            String cp = buildClasspath();

            int result = compiler.run(null, null, null,
                "-classpath", cp, "-d", CLASSES_DIR, src.getAbsolutePath());

            if (result != 0) { log.error("Falha ao compilar {}", handlerFile); return; }

            // 2. carregar .class — fecha loader antigo se existir
            URLClassLoader old = loaders.get(path);
            if (old != null) { try { old.close(); } catch (Exception ignored) {} }

            URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{new File(CLASSES_DIR).toURI().toURL()},
                getClass().getClassLoader());
            loaders.put(path, loader);

            // nome da classe = nome do arquivo sem .java
            String className = src.getName().replace(".java", "");
            Class<?> cls = loader.loadClass(className);
            Object instance = cls.getDeclaredConstructor().newInstance();

            // 3. injetar campos públicos que existam como beans no contexto
            for (Field field : cls.getFields()) {
                try {
                    Object bean = ctx.getBean(field.getType());
                    field.set(instance, bean);
                } catch (Exception ignored) {}
            }

            // 4. injetar params do endpoints.json
            JsonNode paramsNode = node.get("params");
            if (paramsNode != null) {
                try {
                    Field f = cls.getField("params");
                    f.set(instance, mapper.convertValue(paramsNode, java.util.Map.class));
                } catch (Exception ignored) {}
            }

            instances.put(path, instance);

            // 4. desregistrar mapeamento antigo se existir
            unregister(path);

            // 5. registrar novo mapeamento
            Method handleMethod = cls.getMethod(methodName, HttpServletRequest.class, HttpServletResponse.class);
            RequestMappingInfo.BuilderConfiguration opts = new RequestMappingInfo.BuilderConfiguration();
            if (handlerMapping.getPatternParser() != null)
                opts.setPatternParser(handlerMapping.getPatternParser());
            RequestMappingInfo mapping = RequestMappingInfo
                .paths(path)
                .methods(RequestMethod.GET)
                .options(opts)
                .build();

            handlerMapping.registerMapping(mapping, instance, handleMethod);
            log.info("✅ Endpoint dinâmico registrado: GET {}", path);

        } catch (Exception e) {
            log.error("Erro ao registrar endpoint {}: {}", path, e.getMessage());
        }
    }

    private void unregister(String path) {
        Object old = instances.get(path);
        if (old == null) return;
        try {
            handlerMapping.getHandlerMethods().keySet().stream()
                .filter(m -> m.getPatternValues().contains(path))
                .forEach(handlerMapping::unregisterMapping);
        } catch (Exception ignored) {}
    }

    private void startWatcher() {
        Thread t = new Thread(() -> {
            try {
                Path dir = Paths.get("/app/data/config");
                WatchService ws = dir.getFileSystem().newWatchService();
                dir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY);
                Path handlersDir = Paths.get(HANDLERS_DIR);
                if (handlersDir.toFile().exists())
                    handlersDir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY);

                while (true) {
                    WatchKey key = ws.take();
                    for (WatchEvent<?> ev : key.pollEvents()) {
                        String changed = ev.context().toString();
                        if (changed.equals("endpoints.json") || changed.endsWith(".java")) {
                            Thread.sleep(300);
                            log.info("🔄 Recarregando endpoints dinâmicos ({})", changed);
                            loadAll();
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                log.warn("WatchService DynamicEndpointRegistry encerrado: {}", e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
