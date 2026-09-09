import com.pocsigmet.RedisMetarCacheService;
import java.util.*;

/**
 * Versão testável do MetarFiltroHandler — sem HTTP, sem Spring.
 * Delega para handleTest() que retorna Map diretamente.
 */
public class MetarFiltroHandlerTest extends MetarFiltroHandler {
    // herda handleTest() e buildFiltro() — nada adicional necessário
}
