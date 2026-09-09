# Regras do Agente — POC SIGMET

## Antes de qualquer alteração
1. Mapear TODOS os leitores e escritores do que será mexido
2. Verificar o que schedulers/cleanup podem afetar
3. Só então alterar

## Após qualquer alteração
- NUNCA dizer "pronto", "funcionando" ou "concluído" sem antes executar teste real (log ou curl com resposta válida)
- Não declarar "funcionando" sem evidência de teste real (log ou curl)

## Estrutura de diretórios relevante
- `data/config/` — arquivos de configuração estáticos (nunca apagar): `airports.json`, `endpoints.json`, `handlers/`, `firs_brasil_decea_oficial.geojson`, `risk_polygons.geojson`
- `data/grib2/` — arquivos GRIB2 baixados (TTL 6h)
- `data/` raiz — imagens satélite, HSV, wind_cache (TTL 3h, exceto protegidos)

## Arquivos protegidos do cleanup
- `wind_cache_*.json` — cache de vento extraído do GRIB2
- `hsv_metadata.json` — metadados da análise HSV
- `hsv_updated.txt` — timestamp da última análise HSV
- `portainer.db`
- `*_latest.png`, `*_latest.jpg`

## Endpoints importantes
- `/firs` — FIRs Brasil (classpath, não volume)
- `/metar_top200_sb` — depende de `data/config/airports.json`
- `/metar/filtro`, `/metar/nuvens` — endpoints dinâmicos via `data/config/endpoints.json` + `handlers/`
- `/api/windbarbs?level=flXXX` — depende de `wind_cache_*.json` no Redis + arquivo

## Regras gerais
- Código mínimo — sem verbose, sem código que não contribui diretamente
- Não mover arquivos sem verificar todos os consumidores no Java, nginx e docker-compose
- Bind mounts no docker-compose para arquivos que não podem sumir no rebuild
