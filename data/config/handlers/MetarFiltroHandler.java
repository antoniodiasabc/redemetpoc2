import com.pocsigmet.RedisMetarCacheService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.net.URLClassLoader;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.*;

public class MetarFiltroHandler {

    public RedisMetarCacheService redisMetarCacheService;
    public Map<String, Object> params; // injetado pelo registry via endpoints.json

    public void handle(HttpServletRequest req, HttpServletResponse res) throws Exception {
        String[] tiposReq = req.getParameterValues("tipo");
        List<String> filtros = tiposReq != null && tiposReq.length > 0
            ? Arrays.asList(tiposReq)
            : (List<String>) params.getOrDefault("filtros", List.of("OVC|BKN"));

        String opReq = req.getParameter("op");
        boolean and = !"or".equalsIgnoreCase(
            opReq != null ? opReq : (String) params.getOrDefault("op", "and"));

        Predicate<String> combinado = filtros.stream()
            .map(this::buildFiltro)
            .reduce(and ? m -> true : m -> false, and ? Predicate::and : Predicate::or);

        List<Map<String, String>> result = RedisMetarCacheService.getAllCached().entrySet().stream()
            .filter(e -> e.getValue().metarText != null && combinado.test(e.getValue().metarText))
            .map(e -> Map.of("icao", e.getKey(), "condicao", e.getValue().condition, "metar", e.getValue().metarText))
            .sorted(Comparator.comparing(m -> m.get("icao")))
            .collect(Collectors.toList());

        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(String.format(
            "{\"filtros\":%s,\"op\":\"%s\",\"total\":%d,\"aerodromos\":%s}",
            filtros.stream().map(f -> "\"" + f + "\"").collect(Collectors.joining(",", "[", "]")),
            and ? "and" : "or",
            result.size(),
            result.stream().map(a -> String.format(
                "{\"icao\":\"%s\",\"condicao\":\"%s\",\"metar\":\"%s\"}",
                a.get("icao"), a.get("condicao"), esc(a.get("metar")))
            ).collect(Collectors.joining(",", "[", "]"))
        ));
    }

    public Map<String, Object> handleTest(List<String> filtros, String op) {
        boolean and = !"or".equalsIgnoreCase(op);
        Predicate<String> combinado = filtros.stream()
            .map(this::buildFiltro)
            .reduce(and ? m -> true : m -> false, and ? Predicate::and : Predicate::or);

        List<Map<String, String>> aerodromos = RedisMetarCacheService.getAllCached().entrySet().stream()
            .filter(e -> e.getValue().metarText != null && combinado.test(e.getValue().metarText))
            .map(e -> Map.of("icao", e.getKey(), "condicao", e.getValue().condition, "metar", e.getValue().metarText))
            .sorted(Comparator.comparing(m -> m.get("icao")))
            .collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("filtros", filtros);
        out.put("op", op);
        out.put("total", aerodromos.size());
        out.put("aerodromos", aerodromos);
        return out;
    }

    private Predicate<String> buildFiltro(String tipo) {
        Map<String, Predicate<String>> palavras = new HashMap<>();
        palavras.put("rajada",     m -> m.matches(".*\\d{5}G\\d{2,3}KT.*"));
        palavras.put("trovoada",   m -> m.contains("TS"));
        palavras.put("nevoeiro",   m -> m.contains("FG"));
        palavras.put("nevoa",      m -> m.contains("BR"));
        palavras.put("chuva",      m -> m.contains("RA") || m.contains("SH"));
        palavras.put("congelante", m -> m.contains("FZ"));
        palavras.put("cb",         m -> m.matches(".*[\\d/]{3}CB.*"));
        palavras.put("variavel",   m -> m.matches(".*\\d{3}V\\d{3}.*"));

        String lower = tipo.toLowerCase();
        if (palavras.containsKey(lower)) return palavras.get(lower);

        // suporte a temp<N e temp>N
        java.util.regex.Matcher mTemp = java.util.regex.Pattern.compile("^(temp[<>])(\\d+)$").matcher(lower);
        if (mTemp.matches()) {
            String cmd = mTemp.group(1); int val = Integer.parseInt(mTemp.group(2));
            for (MetarFiltro f : loadFiltros()) if (f.aceita(cmd)) return f.buildPredicate(cmd, val);
        }

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([a-z]+)(\\d+)$").matcher(lower);
        if (m.matches()) {
            String cmd = m.group(1); int val = Integer.parseInt(m.group(2));
            for (MetarFiltro f : loadFiltros()) if (f.aceita(cmd)) return f.buildPredicate(cmd, val);
        }

        try { return java.util.regex.Pattern.compile(tipo).asPredicate(); }
        catch (Exception e) { return metar -> metar.contains(tipo); }
    }

    private List<MetarFiltro> loadFiltros() {
        List<MetarFiltro> result = new ArrayList<>();
        File dir = new File("/tmp/dynamic-handlers/");
        if (dir.exists()) {
            URLClassLoader loader = (URLClassLoader) getClass().getClassLoader();
            for (File f : dir.listFiles((d, n) -> n.endsWith(".class"))) {
                try {
                    Class<?> cls = loader.loadClass(f.getName().replace(".class", ""));
                    if (MetarFiltro.class.isAssignableFrom(cls) && !cls.isInterface())
                        result.add((MetarFiltro) cls.getDeclaredConstructor().newInstance());
                } catch (Exception ignored) {}
            }
        }
        if (result.isEmpty()) result = Arrays.asList(
            new MetarFiltroTeto(), new MetarFiltroVis(), new MetarFiltroVento(),
            new MetarFiltroUmidade(), new MetarFiltroPressao(), new MetarFiltroTemp());
        return result;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
