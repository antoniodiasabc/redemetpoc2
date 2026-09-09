import com.pocsigmet.RedisMetarCacheService;
import java.util.*;
import java.util.stream.*;

/** Versão testável do NuvensContagemHandler sem dependência de HttpServletRequest */
public class NuvensContagemHandlerTest {

    public Map<String, Object> handleTest(String tipo) {
        Map<String, RedisMetarCacheService.CachedMetarData> todos =
            RedisMetarCacheService.getAllCached();

        List<String> encontrados = todos.entrySet().stream()
            .filter(e -> e.getValue().metarText != null && e.getValue().metarText.contains(tipo))
            .map(Map.Entry::getKey)
            .sorted()
            .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tipo", tipo);
        result.put("total", encontrados.size());
        result.put("aerodromos", encontrados);
        return result;
    }
}
