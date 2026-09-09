import com.pocsigmet.RedemetMetarClient;
import com.pocsigmet.AirportConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;

public class TesteBulkHandler {

    public RedemetMetarClient redemetMetarClient;

    public void handle(HttpServletRequest req, HttpServletResponse res) throws Exception {
        res.setContentType("application/json;charset=UTF-8");

        List<String> icaos = new ArrayList<>();
        for (Object[] a : AirportConfig.getAirports()) icaos.add((String) a[0]);

        long t0 = System.currentTimeMillis();
        Map<String, String> result = redemetMetarClient.getLatestMetarBulk(icaos);
        long elapsed = System.currentTimeMillis() - t0;

        long found = result.values().stream().filter(v -> !v.contains("não disponível")).count();
        res.getWriter().write(String.format(
            "{\"total_icaos\":%d,\"com_metar\":%d,\"sem_metar\":%d,\"tempo_ms\":%d}",
            icaos.size(), found, icaos.size() - found, elapsed
        ));
    }
}
