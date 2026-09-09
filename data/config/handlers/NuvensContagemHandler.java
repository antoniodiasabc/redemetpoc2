import com.pocsigmet.RedisMetarCacheService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.*;
import java.util.regex.*;

/**
 * Handler dinâmico: conta aeródromos cujo METAR bate com um padrão regex.
 * GET /nuvens/contagem?tipo=OVC|BKN  (default: OVC|BKN)
 */
public class NuvensContagemHandler {

    public RedisMetarCacheService redisMetarCacheService;

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
    }

    private static final java.util.List<MetarFiltro> FILTROS = java.util.Arrays.asList(
        new MetarFiltroTeto(), new MetarFiltroVis(), new MetarFiltroVento(), new MetarFiltroUmidade(), new MetarFiltroPressao()
    );

    private static final java.util.Map<String, java.util.function.Predicate<String>> PALAVRAS = new java.util.HashMap<>();
    static {
        PALAVRAS.put("rajada",     metar -> java.util.regex.Pattern.compile("\\d{5}G\\d{2,3}KT").matcher(metar).find());
        PALAVRAS.put("trovoada",   metar -> metar.contains("TS"));
        PALAVRAS.put("nevoeiro",   metar -> metar.contains("FG"));
        PALAVRAS.put("nevoa",      metar -> metar.contains("BR"));
        PALAVRAS.put("chuva",      metar -> metar.contains("RA") || metar.contains("SH"));
        PALAVRAS.put("congelante", metar -> metar.contains("FZ"));
        PALAVRAS.put("cb",         metar -> java.util.regex.Pattern.compile("[\\d/]{3}CB").matcher(metar).find());
    }

    private static java.util.function.Predicate<String> buildFiltro(String tipo) {
        String lower = tipo.toLowerCase();

        // palavras simples sem valor
        if (PALAVRAS.containsKey(lower)) return PALAVRAS.get(lower);

        // filtros com valor numérico: teto2000, vis5000, vento30, umidade50
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([a-z]+)(\\d+)$").matcher(lower);
        if (m.matches()) {
            String cmd = m.group(1);
            int val = Integer.parseInt(m.group(2));
            for (MetarFiltro f : FILTROS) {
                if (f.aceita(cmd)) return f.buildPredicate(cmd, val);
            }
        }

        // fallback: regex ou literal
        java.util.regex.Pattern p;
        try { p = java.util.regex.Pattern.compile(tipo); }
        catch (Exception e) { p = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(tipo)); }
        final java.util.regex.Pattern pf = p;
        return metar -> pf.matcher(metar).find();
    }

    public void handle(HttpServletRequest req, HttpServletResponse res) throws Exception {
        String tipo = req.getParameter("tipo");
        if (tipo == null || tipo.isBlank()) tipo = "OVC|BKN";
        res.setContentType("application/json;charset=UTF-8");

        Map<String, RedisMetarCacheService.CachedMetarData> todos = RedisMetarCacheService.getAllCached();

        // Palavras mágicas: teto<N>, vis<N>, vento<N> — sem regex, sem caracteres especiais
        final java.util.function.Predicate<String> filtroFinal = buildFiltro(tipo);
        List<Map<String, String>> encontrados = todos.entrySet().stream()
            .filter(e -> e.getValue().metarText != null && filtroFinal.test(e.getValue().metarText))
            .map(e -> Map.of(
                "icao",     e.getKey(),
                "condicao", e.getValue().condition,
                "metar",    e.getValue().metarText
            ))
            .sorted(Comparator.comparing(m -> m.get("icao")))
            .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("{\"tipo\":\"").append(esc(tipo)).append("\",")
          .append("\"total\":").append(encontrados.size()).append(",")
          .append("\"aerodromos\":[");
        for (int i = 0; i < encontrados.size(); i++) {
            Map<String, String> a = encontrados.get(i);
            sb.append("{\"icao\":\"").append(a.get("icao")).append("\",")
              .append("\"condicao\":\"").append(a.get("condicao")).append("\",")
              .append("\"metar\":\"").append(esc(a.get("metar"))).append("\"}");
            if (i < encontrados.size() - 1) sb.append(",");
        }
        sb.append("]}");
        res.getWriter().write(sb.toString());
    }
}
