import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import com.pocsigmet.mongo.MetarDocument;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class MetarVariacaoHandler {

    public MongoTemplate mongoTemplate;

    public void handle(HttpServletRequest req, HttpServletResponse res) throws Exception {
        String varParam = req.getParameter("variacao");
        double limiar = 10.0;
        int sinal = 0; // 0=ambos, 1=subiu, -1=caiu
        if (varParam != null && !varParam.isBlank()) {
            String v = varParam.trim();
            if (v.startsWith("+") || v.endsWith("+")) { sinal = 1;  limiar = Double.parseDouble(v.replace("+","")); }
            else if (v.startsWith("-") || v.endsWith("-")) { sinal = -1; limiar = Double.parseDouble(v.replace("-","")); }
            else { try { limiar = Double.parseDouble(v); } catch (Exception ignored) {} }
        }
        List<String> icaos = mongoTemplate.findDistinct(new Query(), "icao", MetarDocument.class, String.class);
        Map<String, Object> out = buildResult(icaos, limiar, sinal);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(out));
    }

    public Map<String, Object> handleTest(double limiar, int sinal) throws Exception {
        List<String> icaos = mongoTemplate.findDistinct(new Query(), "icao", MetarDocument.class, String.class);
        return buildResult(icaos, limiar, sinal);
    }

    private Map<String, Object> buildResult(List<String> icaos, double limiar, int sinal) {
        List<Map<String, Object>> resultado = new ArrayList<>();

        for (String icao : icaos) {
            Query q = Query.query(Criteria.where("icao").is(icao));
            q.with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(10);
            List<MetarDocument> raw = mongoTemplate.find(q, MetarDocument.class);
            // deduplica mantendo só a primeira ocorrência de cada texto
            List<MetarDocument> docs = raw.stream()
                .filter(new java.util.function.Predicate<MetarDocument>() {
                    final Set<String> seen = new LinkedHashSet<>();
                    public boolean test(MetarDocument d) { return seen.add(d.getMetarText()); }
                })
                .limit(2).collect(java.util.stream.Collectors.toList());
            if (docs.size() < 2) continue;

            MetarDocument atual    = docs.get(0);
            MetarDocument anterior = docs.get(1);

            Map<String, double[]> camposAtual    = parseCampos(atual.getMetarText());
            Map<String, double[]> camposAnterior = parseCampos(anterior.getMetarText());

            Map<String, Map<String, Object>> variacoes = new LinkedHashMap<>();
            for (String campo : camposAtual.keySet()) {
                if (!camposAnterior.containsKey(campo)) continue;
                double vAtual    = camposAtual.get(campo)[0];
                double vAnterior = camposAnterior.get(campo)[0];
                if (vAnterior == 0) continue;
                double pct = Math.abs((vAtual - vAnterior) / vAnterior) * 100.0;
                boolean subiu = vAtual > vAnterior;
                if (pct < limiar) continue;
                if (sinal == 1 && !subiu) continue;
                if (sinal == -1 && subiu) continue;
                // vento: só alerta se ambos os valores >= 5kt
                if (campo.equals("vento") && (vAtual < 5 || vAnterior < 5)) continue;
                // visibilidade e teto: só alerta se for piora (atual < anterior)
                if ((campo.equals("visibilidade") || campo.equals("teto")) && vAtual >= vAnterior) continue;
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("anterior", vAnterior);
                v.put("atual", vAtual);
                v.put("variacao_pct", Math.round(pct * 10.0) / 10.0);
                variacoes.put(campo, v);
            }

            if (!variacoes.isEmpty()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("icao", icao);
                entry.put("timestamp_atual", atual.getTimestamp());
                entry.put("msg_anterior", anterior.getMetarText());
                entry.put("msg_atual", atual.getMetarText());
                entry.put("variacoes", variacoes);
                resultado.add(entry);
            }
        }

        resultado.sort(Comparator.comparingLong((Map<String, Object> m) -> (long) m.get("timestamp_atual")).reversed());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("limiar_pct", limiar);
        out.put("total", resultado.size());
        out.put("aerodromos", resultado);
        return out;
    }

    private Map<String, double[]> parseCampos(String metar) {
        Map<String, double[]> m = new LinkedHashMap<>();
        if (metar == null) return m;

        Matcher t = Pattern.compile("\\b(M?\\d{2})/(M?\\d{2})\\b").matcher(metar);
        if (t.find()) {
            m.put("temp",    new double[]{parseTemp(t.group(1))});
            m.put("orvalho", new double[]{parseTemp(t.group(2))});
        }

        Matcher q = Pattern.compile("\\bQ(\\d{4})\\b").matcher(metar);
        if (q.find()) m.put("pressao", new double[]{Double.parseDouble(q.group(1))});

        Matcher w = Pattern.compile("\\b(\\d{3}|VRB)(\\d{2,3})(?:G(\\d{2,3}))?KT\\b").matcher(metar);
        if (w.find()) {
            m.put("vento", new double[]{Double.parseDouble(w.group(2))});
            if (w.group(3) != null) m.put("rajada", new double[]{Double.parseDouble(w.group(3))});
        }

        Matcher v = Pattern.compile("(?:^|\\s)(\\d{4})(?:\\s|$)").matcher(metar);
        if (v.find()) m.put("visibilidade", new double[]{Double.parseDouble(v.group(1))});

        Matcher c = Pattern.compile("\\b(?:BKN|OVC)(\\d{3})\\b").matcher(metar);
        double teto = Double.MAX_VALUE;
        while (c.find()) teto = Math.min(teto, Double.parseDouble(c.group(1)) * 100);
        if (teto < Double.MAX_VALUE) m.put("teto", new double[]{teto});

        return m;
    }

    private double parseTemp(String s) {
        return s.startsWith("M") ? -Double.parseDouble(s.substring(1)) : Double.parseDouble(s);
    }
}
