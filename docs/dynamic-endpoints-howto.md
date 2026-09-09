# Como criar um endpoint dinâmico

## Modo novo — só o .java (sem tocar no endpoints.json)

Crie o arquivo em `data/config/handlers/MeuHandler.java`:

```java
@com.pocsigmet.DynamicEndpoint(path = "/metar/chuva")
public class MetarFiltroChuva {

    public com.pocsigmet.RedisMetarCacheService redisMetarCacheService; // injetado automaticamente

    public void handle(jakarta.servlet.http.HttpServletRequest req,
                       jakarta.servlet.http.HttpServletResponse res) throws Exception {
        // lógica aqui
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"ok\":true}");
    }
}
```

O watcher detecta o arquivo novo, compila e registra o endpoint automaticamente — sem restart.

## Modo antigo — endpoints.json (ainda funciona)

Adicione entrada em `data/config/endpoints.json`:
```json
{
  "path": "/metar/chuva",
  "handlerClass": "/app/data/config/handlers/MetarFiltroChuva.java",
  "method": "handle"
}
```

## Observações
- O campo público com tipo de bean Spring é injetado automaticamente (ex: `RedisMetarCacheService`)
- O método obrigatório é `handle(HttpServletRequest, HttpServletResponse)`
- Para filtros numéricos (ex: `vis8000`, `teto1000`) implemente `MetarFiltro` e registre via `MetarFiltroHandler` existente
- Exemplo real: `data/config/handlers/MetarFiltroVis.java`
