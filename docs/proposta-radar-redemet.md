# Proposta: Radar REDEMET no Mapa

## Fonte
`https://redemet.decea.mil.br/old/produtos/radares-meteorologicos/plota_radar.php`
- POST com `radar[]=maxcappi` — sem autenticação
- Retorna chamadas `carrega_radar()` com URL da imagem + bounding box de cada radar

## Padrão da URL da imagem
```
https://redemet.decea.mil.br/old/radar/YYYY/MM/DD/{sigla}/maxcappi/maps/YYYY-MM-DD--HH:MM:SS.png
```

## Radares disponíveis
| Sigla | Local |
|---|---|
| `be` | Belém/PA |
| `bv` | Boa Vista/RR |
| `cn` | Canguçu/RS |
| `jr` | Jaraguari/MS |
| `mi` | Morro da Igreja/SC |
| `mn` | Manaus/AM |
| `mo` | Maceió/AL |
| `nt` | Natal/RN |
| `pc` | Pico do Couto/RJ |
| `pv` | Porto Velho/RO |
| `sg` | Santiago/RS |
| `sl` | São Luiz/MA |
| `st` | Santa Tereza/ES |
| `tt` | Tabatinga/AM |
| `ua` | São Gabriel da Cachoeira/AM |

## Características da imagem
- 400×400 pixels, RGBA
- 99% de pixels transparentes — fundo já transparente, pronto para overlay
- Bounding box (norte, sul, leste, oeste) já vem no response do PHP

## Classes novas
1. `RadarRedemtService.java` — chama o PHP, extrai URLs + bounding boxes, agenda refresh a cada 10min
2. `RadarController.java` — expõe `GET /api/radar/latest` retornando JSON com lista de radares

## Classes alteradas
- `ImageAndGrib2Scheduler.java` — adicionar chamada ao `RadarRedemtService`
- `index.html` — painel toggle + overlay no Cesium (3D) e OpenLayers (2D)

## Fluxo
```
Scheduler (10min)
  → RadarRedemtService.fetch()
    → POST plota_radar.php
    → extrai carrega_radar() params
    → guarda em memória

GET /api/radar/latest
  → retorna lista JSON

Front (setInterval 10min)
  → fetch /api/radar/latest
  → atualiza RectangleGraphics no Cesium (3D)
  → atualiza ol.layer.Image + ol.source.ImageStatic no OpenLayers (2D)
```

## Como aparece na tela
Painel lateral com toggle individual por radar — controla ambos os modos simultaneamente:
```
🟢 RADAR MAXCAPPI          [ON/OFF]
☑ Belém/PA      14:38
☑ Manaus/AM     14:43
☑ Pico do Couto 14:46
☑ Natal/RN      14:47
...
```
- Cada radar sobreposto no mapa na posição geográfica correta (3D e 2D)
- Clique no nome centraliza o mapa no radar
- Auto-refresh a cada 10min

## Plano de implementação (incremental)

### Etapa 1 — 1 radar fixo (Pico do Couto/RJ - `pc`)
- `RadarRedemtService.java` — chama o PHP, extrai URL + bbox do `pc`, guarda em memória, refresh 10min
- `RadarController.java` — `GET /api/radar/latest` retorna JSON com o radar `pc`
- `index.html` 3D — `RectangleGraphics` no Cesium com a imagem + botão ON/OFF
- `index.html` 2D — `ol.layer.Image` + `ol.source.ImageStatic` com o mesmo bbox + mesmo botão ON/OFF

**Validar:** imagem aparece correta nos dois modos, transparência ok, atualiza 10min

### Etapa 2 — Todos os radares
- Expandir `RadarRedemtService` para iterar todos os 15 radares
- Painel lateral com lista e toggle individual por radar — controla ambos os modos simultaneamente

### Etapa 3 — Animação (opcional)
- Últimas N imagens por radar para animação
- Controle de velocidade no painel
