# Plano de Migração para Spring Boot 100%

## Regras Gerais

- **Uma PR por endpoint** — nunca migrar dois ao mesmo tempo
- **Backup antes de cada migração:** `cp PocSigmetApplication.java PocSigmetApplication.java.bak_YYYYMMDD`
- **Teste antes:** curl no endpoint atual, salvar output como baseline
- **Teste depois:** mesmo curl, comparar output com baseline
- **Rollback:** se teste falhar, `cp PocSigmetApplication.java.bak_YYYYMMDD PocSigmetApplication.java` + rebuild
- **Nunca remover** o handler do `PocSigmetApplication.java` antes do teste passar

---

## Estrutura Spring Boot Alvo

```
controller/
  HealthController.java
  MetarController.java
  SigmetController.java
  OpmetController.java
  SigwxController.java
  WindController.java
  ConvectionController.java
  AnimationController.java
  MonitoringController.java
service/
  (já existem — apenas injetar via @Autowired)
scheduler/
  DownloadScheduler.java
  CleanupScheduler.java
  HSVScheduler.java
config/
  WebSocketConfig.java
  RedisConfig.java
```

---

## Fases de Migração

### FASE 0 — Preparação (sem mexer em endpoint nenhum)

**Objetivo:** Criar estrutura Spring Boot ao lado do código atual, sem quebrar nada.

- [ ] Adicionar `@SpringBootApplication` no `PocSigmetApplication.java` (já tem?)
- [ ] Criar `src/main/resources/application.yml` com configs externalizadas
- [ ] Verificar que `pom.xml` tem `spring-boot-starter-web` e `spring-boot-starter-actuator`
- [ ] Criar pacotes `controller/`, `scheduler/`, `config/`
- [ ] **Teste:** rebuild + todos os 32 endpoints respondem igual ao baseline

**Baseline a salvar:**
```bash
./scripts/test-baseline.sh  # ver seção de scripts
```

---

### FASE 1 — Endpoints Simples (sem dependência externa)

#### 1.1 `/health`
- **Handler atual:** `HealthHandler`
- **Spring:** `@GetMapping("/health")` em `HealthController`
- **Teste:** `curl http://localhost/health` → `{"status":"UP",...}`
- **Risco:** Baixo

#### 1.2 `/sigmet/count`
- **Handler atual:** `handleSigmetCount`
- **Spring:** `@GetMapping("/sigmet/count")` em `SigmetController`
- **Teste:** `curl http://localhost/sigmet/count`
- **Risco:** Baixo

#### 1.3 `/firs`
- **Handler atual:** serve arquivo GeoJSON estático
- **Spring:** `@GetMapping("/firs")` servindo arquivo de `data/`
- **Teste:** `curl http://localhost/firs | python3 -m json.tool | head -5`
- **Risco:** Baixo

#### 1.4 `/hsv_metadata`
- **Handler atual:** lê `data/hsv_metadata.json`
- **Spring:** `@GetMapping("/hsv_metadata")`
- **Teste:** `curl http://localhost/hsv_metadata`
- **Risco:** Baixo

#### 1.5 `/cache/stats`
- **Handler atual:** `handleCacheStats`
- **Spring:** `@GetMapping("/cache/stats")` em `MonitoringController`
- **Teste:** `curl http://localhost/cache/stats`
- **Risco:** Baixo

#### 1.6 `/monitoring/stats`
- **Handler atual:** `handleMonitoringStats`
- **Spring:** `@GetMapping("/monitoring/stats")` em `MonitoringController`
- **Teste:** `curl http://localhost/monitoring/stats`
- **Risco:** Baixo

---

### FASE 2 — Endpoints de Imagem/Arquivo

#### 2.1 `/canal16`
- **Handler atual:** serve imagem JPG mais recente de `data/`
- **Spring:** `@GetMapping(value="/canal16", produces=MediaType.IMAGE_JPEG_VALUE)`
- **Teste:** `curl -o /tmp/test.jpg http://localhost/canal16 && file /tmp/test.jpg`
- **Risco:** Médio (binary response)

#### 2.2 `/vis`
- **Handler atual:** serve PNG de `data/vis_latest.png`
- **Spring:** `@GetMapping(value="/vis", produces=MediaType.IMAGE_PNG_VALUE)`
- **Teste:** `curl -o /tmp/test.png http://localhost/vis && file /tmp/test.png`
- **Risco:** Médio

#### 2.3 `/frames`
- **Handler atual:** lista frames disponíveis
- **Spring:** `@GetMapping("/frames")`
- **Teste:** `curl http://localhost/frames | python3 -m json.tool`
- **Risco:** Baixo

#### 2.4 `/canal16frames`
- **Handler atual:** lista frames canal16
- **Spring:** `@GetMapping("/canal16frames")`
- **Teste:** `curl http://localhost/canal16frames | python3 -m json.tool`
- **Risco:** Baixo

#### 2.5 `/convection`
- **Handler atual:** serve GeoJSON de convecção
- **Spring:** `@GetMapping("/convection")`
- **Teste:** `curl http://localhost/convection | python3 -m json.tool | head -10`
- **Risco:** Baixo

#### 2.6 `/hsv_polygons`
- **Handler atual:** `handleHSVPolygons`
- **Spring:** `@GetMapping("/hsv_polygons")`
- **Teste:** `curl http://localhost/hsv_polygons | python3 -m json.tool | head -10`
- **Risco:** Baixo

#### 2.7 `/risk_polygons.geojson`
- **Handler atual:** serve arquivo estático
- **Spring:** `@GetMapping("/risk_polygons.geojson")`
- **Teste:** `curl http://localhost/risk_polygons.geojson | python3 -m json.tool | head -5`
- **Risco:** Baixo

#### 2.8 `/severe_convection_with_sigmet.geojson`
- **Handler atual:** serve arquivo estático
- **Spring:** `@GetMapping("/severe_convection_with_sigmet.geojson")`
- **Teste:** `curl http://localhost/severe_convection_with_sigmet.geojson | python3 -m json.tool | head -5`
- **Risco:** Baixo

---

### FASE 3 — Endpoints METAR

#### 3.1 `/metar_sbsp`
- **Handler atual:** `handleMetarSbsp`
- **Spring:** `@GetMapping("/metar_sbsp")` em `MetarController`
- **Teste:** `curl http://localhost/metar_sbsp`
- **Risco:** Baixo

#### 3.2 `/metar_sboi`
- **Handler atual:** `handleMetarSboi`
- **Spring:** `@GetMapping("/metar_sboi")`
- **Teste:** `curl http://localhost/metar_sboi`
- **Risco:** Baixo

#### 3.3 `/metar_all`
- **Handler atual:** `handleMetarAll`
- **Spring:** `@GetMapping("/metar_all")`
- **Teste:** `curl http://localhost/metar_all | python3 -m json.tool | head -20`
- **Risco:** Médio (depende Redis)

#### 3.4 `/metar_top20_sb`, `/metar_top50_sb`, `/metar_top200_sb`
- **Handler atual:** `handleMetarTopSB(exchange, limit)`
- **Spring:** `@GetMapping("/metar_top{limit}_sb")` com `@PathVariable`
- **Teste:** `curl http://localhost/metar_top20_sb | python3 -m json.tool | head -10`
- **Risco:** Baixo

#### 3.5 `/api/v1/alerts/metar`
- **Handler atual:** `handleMetarAlerts`
- **Spring:** `@GetMapping("/api/v1/alerts/metar")` em `MetarController`
- **Teste:** `curl http://localhost/api/v1/alerts/metar | python3 -m json.tool`
- **Risco:** Médio (lógica de alertas complexa)

---

### FASE 4 — Endpoints SIGMET/REDEMET

#### 4.1 `/sigmet`
- **Handler atual:** `handleSigmet` (busca SIGMET por FIR)
- **Spring:** `@GetMapping("/sigmet")` com `@RequestParam`
- **Teste:** `curl "http://localhost/sigmet?fir=SBRE"`
- **Risco:** Médio (chamada externa REDEMET)

#### 4.2 `/redemet_sigmets`
- **Handler atual:** `handleRedemetSigmets`
- **Spring:** `@GetMapping("/redemet_sigmets")`
- **Teste:** `curl http://localhost/redemet_sigmets`
- **Risco:** Médio

#### 4.3 `/redemet_sigmets_json`
- **Handler atual:** `handleRedemetSigmetsJson` (async com timeout)
- **Spring:** `@GetMapping("/redemet_sigmets_json")` com `CompletableFuture`
- **Teste:** `curl http://localhost/redemet_sigmets_json`
- **Risco:** Alto (async + timeout)

#### 4.4 `/redemet_sigmets_all`
- **Handler atual:** `handleRedemetSigmetsAll`
- **Spring:** `@GetMapping("/redemet_sigmets_all")`
- **Teste:** `curl http://localhost/redemet_sigmets_all | python3 -m json.tool | head -20`
- **Risco:** Médio

#### 4.5 `/sigmet_copilot`
- **Handler atual:** processa SIGMET com IA/análise
- **Spring:** `@PostMapping("/sigmet_copilot")`
- **Teste:** `curl -X POST http://localhost/sigmet_copilot -d '...'`
- **Risco:** Alto (lógica complexa)

#### 4.6 `/create_sigmet`
- **Handler atual:** `handleCreateSigmet`
- **Spring:** `@PostMapping("/create_sigmet")`
- **Teste:** POST com payload de teste
- **Risco:** Alto

---

### FASE 5 — Endpoints Wind/SigWx

#### 5.1 `/api/wind/barbs/fl050`
- **Handler atual:** retorna dados de vento GRIB2
- **Spring:** `@GetMapping("/api/wind/barbs/fl050")` em `WindController`
- **Teste:** `curl http://localhost/api/wind/barbs/fl050 | python3 -m json.tool | head -10`
- **Risco:** Médio (depende GRIB2)

#### 5.2 `/api/wind/barbs/fl390`
- **Handler atual:** idem fl050
- **Spring:** `@GetMapping("/api/wind/barbs/fl390")`
- **Teste:** `curl http://localhost/api/wind/barbs/fl390 | python3 -m json.tool | head -10`
- **Risco:** Médio

#### 5.3 `/api/v1/sigwx/current`
- **Handler atual:** retorna GeoJSON SigWx
- **Spring:** `@GetMapping("/api/v1/sigwx/current")` em `SigwxController`
- **Teste:** `curl http://localhost/api/v1/sigwx/current | python3 -m json.tool | head -10`
- **Risco:** Médio

#### 5.4 `/api/v1/sigwx/meta`
- **Handler atual:** metadados SigWx
- **Spring:** `@GetMapping("/api/v1/sigwx/meta")`
- **Teste:** `curl http://localhost/api/v1/sigwx/meta`
- **Risco:** Baixo

---

### FASE 6 — Endpoints HSV/Imagem Processada

#### 6.1 `/hsv_optimized`
- **Handler atual:** `handleHSVOptimized` (dispara análise HSV)
- **Spring:** `@PostMapping("/hsv_optimized")` em `ConvectionController`
- **Teste:** `curl -X POST http://localhost/hsv_optimized`
- **Risco:** Alto (OpenCV, async)

#### 6.2 `/realcada_hsv`
- **Handler atual:** `handleRealcadaHSV`
- **Spring:** `@PostMapping("/realcada_hsv")`
- **Teste:** `curl -X POST http://localhost/realcada_hsv`
- **Risco:** Alto

#### 6.3 `/make_transparent`
- **Handler atual:** `handleMakeTransparent`
- **Spring:** `@PostMapping("/make_transparent")`
- **Teste:** POST com imagePath
- **Risco:** Médio

#### 6.4 `/transparent_image`
- **Handler atual:** `handleTransparentImage`
- **Spring:** `@GetMapping("/transparent_image")`
- **Teste:** `curl http://localhost/transparent_image`
- **Risco:** Médio

#### 6.5 `/grib2_info`
- **Handler atual:** `handleGrib2Info`
- **Spring:** `@GetMapping("/grib2_info")`
- **Teste:** `curl http://localhost/grib2_info`
- **Risco:** Médio

---

### FASE 7 — Endpoints OPMET (mais críticos)

#### 7.1 `/api/v1/opmet/messages`
- **Handler atual:** `handleOpmetMessages`
- **Spring:** `@GetMapping("/api/v1/opmet/messages")` em `OpmetController`
- **Teste:** `curl http://localhost/api/v1/opmet/messages | python3 -m json.tool`
- **Risco:** Alto (depende SSE ativo)

#### 7.2 `/api/v1/opmet/test-message`
- **Handler atual:** `handleOpmetTestMessage`
- **Spring:** `@PostMapping("/api/v1/opmet/test-message")`
- **Teste:** `curl -X POST http://localhost/api/v1/opmet/test-message`
- **Risco:** Médio

#### 7.3 `/opmet_live`
- **Handler atual:** `handleOpmetLive` (serve página HTML)
- **Spring:** `@GetMapping("/opmet_live")` retornando HTML ou redirect
- **Teste:** `curl http://localhost/opmet_live | head -20`
- **Risco:** Médio

#### 7.4 `/ws/opmet`
- **Handler atual:** WebSocket manual
- **Spring:** `WebSocketHandler` + `WebSocketConfig` com `@EnableWebSocket`
- **Teste:** `wscat -c ws://localhost/ws/opmet`
- **Risco:** **Muito Alto** — fazer por último

#### 7.5 `/api/v1/animation/realcada-images`
- **Handler atual:** `handleRealcadaImagesEndpoint`
- **Spring:** `@GetMapping("/api/v1/animation/realcada-images")`
- **Teste:** `curl http://localhost/api/v1/animation/realcada-images | python3 -m json.tool`
- **Risco:** Baixo

---

### FASE 8 — Schedulers

#### 8.1 Download de Imagens
- **Atual:** `scheduler.scheduleWithFixedDelay` a cada 60s
- **Spring:** `@Scheduled(fixedDelay=60000)` em `DownloadScheduler`
- **Risco:** Médio (lock Redis deve ser mantido)

#### 8.2 HSV Analysis
- **Atual:** `scheduler.scheduleWithFixedDelay` a cada 10min
- **Spring:** `@Scheduled(fixedDelay=600000)` em `HSVScheduler`
- **Risco:** Médio

#### 8.3 Cleanup de Arquivos
- **Atual:** `cleanupScheduler.scheduleWithFixedDelay`
- **Spring:** `@Scheduled` em `CleanupScheduler`
- **Risco:** Baixo

#### 8.4 OPMET Conexão Permanente
- **Atual:** `OpmetWebSocketHandler.initPermanentConnection()` no startup
- **Spring:** `ApplicationRunner` ou `@EventListener(ApplicationReadyEvent.class)`
- **Risco:** Alto

---

## Scripts de Teste

### Salvar Baseline
```bash
#!/bin/bash
# scripts/save-baseline.sh
ENDPOINTS=(
  "/health"
  "/sigmet/count"
  "/firs"
  "/hsv_metadata"
  "/cache/stats"
  "/monitoring/stats"
  "/frames"
  "/canal16frames"
  "/convection"
  "/metar_sbsp"
  "/metar_sboi"
  "/metar_top20_sb"
  "/api/v1/alerts/metar"
  "/api/v1/opmet/messages"
  "/api/v1/sigwx/current"
  "/api/v1/sigwx/meta"
  "/api/wind/barbs/fl050"
  "/redemet_sigmets_json"
)

mkdir -p baseline
for ep in "${ENDPOINTS[@]}"; do
  name=$(echo $ep | tr '/' '_')
  curl -s "http://localhost$ep" > "baseline/${name}.json"
  echo "Saved: $ep"
done
```

### Comparar com Baseline
```bash
#!/bin/bash
# scripts/compare-baseline.sh
ENDPOINT=$1
name=$(echo $ENDPOINT | tr '/' '_')
curl -s "http://localhost$ENDPOINT" > /tmp/current.json
diff "baseline/${name}.json" /tmp/current.json && echo "✅ OK" || echo "❌ DIFF"
```

### Rollback
```bash
#!/bin/bash
# scripts/rollback.sh
BAK=$1  # ex: PocSigmetApplication.java.bak_20260810
cp src/main/java/com/pocsigmet/$BAK src/main/java/com/pocsigmet/PocSigmetApplication.java
./rebuild.sh
echo "✅ Rollback feito para $BAK"
```

---

## Ordem de Execução Recomendada

```
FASE 0 → FASE 1 → FASE 2 → FASE 3 → FASE 4 → FASE 5 → FASE 6 → FASE 7 → FASE 8
```

Cada fase só começa quando **todos os testes da fase anterior passam**.

Tempo estimado por fase:
- Fase 0: 2h
- Fase 1: 1h
- Fase 2: 2h
- Fase 3: 2h
- Fase 4: 3h
- Fase 5: 2h
- Fase 6: 3h
- Fase 7: 4h (WebSocket é o mais crítico)
- Fase 8: 2h

**Total estimado: ~21h de trabalho seguro**
