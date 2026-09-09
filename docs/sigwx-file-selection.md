# SigWx WAFS — Seleção de Arquivo por Ciclo

## Contexto
Hoje `/api/v1/sigwx/current` sempre resolve o arquivo mais recente automaticamente.
A ideia é permitir que o usuário navegue entre ciclos disponíveis (1 passado + atual + 4 futuros).

## Endpoints novos

| Endpoint | Descrição |
|---|---|
| `GET /api/v1/sigwx/list` | Retorna janela de 6 ciclos já calculada pelo backend (ver formato abaixo) |
| `GET /api/v1/sigwx/file?name=egrr_iwxxm_forecasts_2026-07-25T120000Z.xml` | GeoJSON de arquivo específico. Valida padrão do nome (anti path traversal) |

### Formato da resposta de `/api/v1/sigwx/list`

Array JSON com exatamente 6 entradas, ordenadas cronologicamente:

```json
[
  { "filename": "egrr_iwxxm_forecasts_2026-07-25T090000Z.xml", "label": "25/07 09Z", "available": false, "current": false },
  { "filename": "egrr_iwxxm_forecasts_2026-07-25T120000Z.xml", "label": "25/07 12Z", "available": true,  "current": true  },
  { "filename": "egrr_iwxxm_forecasts_2026-07-25T150000Z.xml", "label": "25/07 15Z", "available": true,  "current": false },
  { "filename": "egrr_iwxxm_forecasts_2026-07-25T180000Z.xml", "label": "25/07 18Z", "available": false, "current": false },
  { "filename": "egrr_iwxxm_forecasts_2026-07-25T210000Z.xml", "label": "25/07 21Z", "available": false, "current": false },
  { "filename": "egrr_iwxxm_forecasts_2026-07-26T000000Z.xml", "label": "26/07 00Z", "available": false, "current": false }
]
```

Campos:
- `filename` — nome do arquivo; chave para chamar `/file?name=`
- `label` — string pronta para exibir no botão (formato `dd/MM HHz`)
- `available` — true se o arquivo existe no disco
- `current` — true para o ciclo que `/current` resolveria agora (exatamente um item true, ou nenhum se nenhum arquivo existe)

Lógica de janela (backend):
- Âncora = UTC atual arredondado para baixo no múltiplo de 3h (ex: 12:39Z → 12Z)
- Janela: âncora −3h, âncora, âncora +3h, +6h, +9h, +12h
- `current` = entrada cuja âncora coincide com o arquivo resolvido por `resolveCurrentFile()`
- `available` = arquivo existe no disco (cruzado com `listAvailableFiles()`)
- Toda a lógica de data/hora e disponibilidade fica no backend; o frontend só renderiza

## Endpoints alterados

| Endpoint | O que muda |
|---|---|
| `/api/v1/sigwx/current` | Nenhuma mudança |
| `/api/v1/sigwx/meta` | Nenhuma mudança |
| Roteador `PocSigmetApplication.java` | Bloco `if (path.startsWith("/api/v1/sigwx/"))` antes do switch, com ifs internos para cada path exato. Instância `SigwxService` estática (criada uma vez com `SIGWX_PATH` do env) compartilhada por todos os handlers sigwx |

## Mudanças no frontend (index.html)

| O que | Detalhe |
|---|---|
| Cache `sigwxGeoJsonCache = null` → `Map()` | Key = filename, value = GeoJSON parsed. Nunca limpo durante a sessão |
| `getSigwxGeoJson(filename)` | Recebe filename como parâmetro. Se null usa `/current`. Consulta Map antes de fetch |
| `loadSigwxList()` | Chama `/api/v1/sigwx/list` uma vez. Armazena o array retornado. Posiciona índice inicial no item com `current: true` |
| `navigateSigwx(direction)` | direction = +1 ou -1. Avança/volta nos ciclos, pula entradas com `available: false`, limpa layers (não o cache Map), exibe `label` do ciclo atual |
| UI no painel SigWx | Adiciona `◀ [label] ▶` acima dos botões de fenômeno. Botão desabilitado nos extremos e quando `available: false` |
| `clearAllSigwx()` | Continua limpando layers — mas não limpa o Map de cache |

Sem lógica de data/hora no frontend. O frontend só itera o array recebido.

## Métodos novos em `SigwxService`

| Método | Contrato |
|---|---|
| `List<String> listAvailableFiles()` | Lista arquivos no disco que batem com o padrão. Retorna lista vazia (não lança exceção) se diretório não existe |
| `File resolveFileByName(String name)` | Valida nome contra regex `egrr_iwxxm_forecasts_\d{4}-\d{2}-\d{2}T\d{6}Z\.xml`. Retorna null se inválido ou não existe. Nunca lança exceção |
| `List<CycleEntry> buildCycleWindow()` | Monta a janela de 6 ciclos. Usa `listAvailableFiles()` e `resolveCurrentFile()` internamente |

`CycleEntry` é um record/POJO simples: `filename`, `label`, `available`, `current`.

## Testes unitários (Java — SigwxServiceTest)

Todos os testes novos usam `@TempDir` — sem dependência de path local.

- `listAvailableFiles()` retorna lista ordenada corretamente
- `listAvailableFiles()` retorna lista vazia se diretório não existe (não lança exceção)
- `parseToGeoJson(file)` com XML de fixture (em `src/test/resources`) retorna GeoJSON com pelo menos um Feature
- `resolveFileByName()` retorna null para nome com `../` (path traversal)
- `resolveFileByName()` retorna null para nome que não bate com o padrão
- `resolveFileByName()` retorna null se arquivo não existe no disco
- `buildCycleWindow()` retorna exatamente 6 entradas
- `buildCycleWindow()` marca `available=true` apenas para arquivos presentes no `@TempDir`
- `buildCycleWindow()` marca exatamente um `current=true` quando arquivo atual existe

Testes existentes que dependem de `/mnt/c/Users/aodias/sigwx` devem ser anotados com `@Disabled("requer ambiente local")`.

## Testes de integração (Java — SigwxControllerIT)

Usam `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `java.net.http.HttpClient`.

- `GET /api/v1/sigwx/list` retorna 200 com array JSON de 6 entradas
- `GET /api/v1/sigwx/list` retorna 200 com array de 6 entradas mesmo se diretório vazio (não 500)
- `GET /api/v1/sigwx/file?name=arquivo_valido.xml` retorna 200 com GeoJSON
- `GET /api/v1/sigwx/file?name=../etc/passwd` retorna 400
- `GET /api/v1/sigwx/file` sem parâmetro retorna 400
- `GET /api/v1/sigwx/file?name=nao_existe.xml` retorna 404
