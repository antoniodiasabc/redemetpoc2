# Plano de Implementação — Visualização 3D SIGWX/SIGMET

## Visão Geral

Adicionar modo 3D à tela atual usando **Cesium.js + ol-cesium**, permitindo alternar entre
o mapa 2D (OpenLayers) e visualização 3D sem trocar de página.

---

## Dados Disponíveis

O endpoint `/api/v1/sigwx/current` já retorna GeoJSON com altitude:

```json
{
  "type": "CLOUD",
  "flUpper": "340",
  "flLower": "80",
  "cloudType": "9"
}
```

- 411 features no total
- 252 com `flUpper` preenchido
- 120 com `flLower` preenchido
- Quando `flLower` vazio → assumir FL000 (superfície)

Conversão FL → metros: `FL * 30.48` (ex: FL340 = 10363m)

---

## Visualização 3D por Fenômeno

| type | visualização | cor sugerida |
|---|---|---|
| CLOUD (cloudType=9 = CB) | polígono extrudado flLower→flUpper | vermelho semitransparente |
| CLOUD (outros) | polígono extrudado | cinza semitransparente |
| TURBULENCE | polígono extrudado | laranja semitransparente |
| ICING | polígono extrudado | azul semitransparente |
| JET STREAM | linha 3D no FL | amarelo |
| TROPOPAUSE | superfície horizontal no FL | verde semitransparente |

---

## Arquitetura

```
index.html
├── mapa 2D (OpenLayers) — padrão atual
└── botão "3D" → inicializa Cesium viewer
    └── ol-cesium sincroniza câmera 2D↔3D
```

## Build do ol-cesium (bundle local)

O ol-cesium não disponibiliza mais bundle UMD via CDN — precisa ser compilado localmente.

### Pré-requisitos
- Node.js + npm instalados

### Comandos

```bash
mkdir -p /tmp/olcs-build && cd /tmp/olcs-build

# instalar dependências (forçar registry oficial)
cat > package.json << 'EOF'
{
  "name": "olcs-build",
  "version": "1.0.0",
  "dependencies": {
    "ol-cesium": "2.17.0",
    "ol": "8.2.0"
  }
}
EOF
npm install --registry https://registry.npmjs.org
npm install --registry https://registry.npmjs.org esbuild

# entry point — expõe OLCesium como window.olcsBundle
cat > entry.js << 'EOF'
import OLCesium from 'ol-cesium/src/olcs/OLCesium.js';
window.olcsBundle = { OLCesium };
EOF

# gerar bundle (Cesium fica externo — carregado via CDN)
./node_modules/.bin/esbuild entry.js --bundle --global-name=olcsBundle --outfile=olcs.js --external:cesium

# copiar para o projeto
cp olcs.js /home/aodias/redemetpoc2_.bk07042026/src/main/resources/static/js/olcs.js
```

> Se precisar regenerar (ex: atualizar versão), basta repetir os comandos acima.

### Uso no HTML

```html
<!-- Cesium via CDN (deve vir antes do olcs.js) -->
<script src="https://cesium.com/downloads/cesiumjs/releases/1.114/Build/Cesium/Cesium.js"></script>
<link href="https://cesium.com/downloads/cesiumjs/releases/1.114/Build/Cesium/Widgets/widgets.css" rel="stylesheet">

<!-- ol-cesium bundle local -->
<script src="/js/olcs.js"></script>
```

No JS usar `olcsBundle.OLCesium` (não `olcs.OLCesium`):

```javascript
ol3d = new olcsBundle.OLCesium({ map: map });
```

---

## Implementação Frontend

### 1. Botão de alternância
```html
<button id="btn-3d" onclick="toggle3D()">🌐 3D</button>
```

### 2. Inicialização ol-cesium
```javascript
let ol3d = null;

function toggle3D() {
    if (!ol3d) {
        ol3d = new olcs.OLCesium({ map: map }); // map = instância OpenLayers existente
        ol3d.getCesiumScene().globe.enableLighting = true;
        plotSigwx3D(ol3d.getCesiumScene());
    }
    ol3d.setEnabled(!ol3d.getEnabled());
    document.getElementById('btn-3d').textContent = ol3d.getEnabled() ? '🗺️ 2D' : '🌐 3D';
}
```

### 3. Plot dos fenômenos
```javascript
async function plotSigwx3D(scene) {
    const data = await fetch('/api/v1/sigwx/current').then(r => r.json());
    const entities = ol3d.getCesiumScene().primitives; // ou viewer.entities

    data.features.forEach(f => {
        const p = f.properties;
        const baseM  = (parseFloat(p.flLower) || 0)   * 30.48;
        const topM   = (parseFloat(p.flUpper) || 3000) * 30.48;
        const color  = colorByType(p.type, p.cloudType);

        if (f.geometry.type === 'Polygon') {
            // extrusão do polígono entre base e topo
            scene.primitives.add(new Cesium.GroundPrimitive({...}));
        }
    });
}

function colorByType(type, cloudType) {
    if (type === 'CLOUD' && cloudType === '9') return Cesium.Color.RED.withAlpha(0.4);
    if (type === 'TURBULENCE') return Cesium.Color.ORANGE.withAlpha(0.4);
    if (type === 'ICING')      return Cesium.Color.BLUE.withAlpha(0.4);
    return Cesium.Color.GRAY.withAlpha(0.3);
}
```

---

## Implementação Backend

Nenhuma mudança necessária — o endpoint `/api/v1/sigwx/current` já retorna
todos os dados necessários incluindo `flUpper`, `flLower` e geometria GeoJSON.

---

## Ordem de Execução

1. Adicionar dependências Cesium + ol-cesium no `index.html`
2. Criar token gratuito em cesium.com e adicionar no `.env` como `CESIUM_TOKEN`
3. Botão 2D/3D + inicialização ol-cesium
4. Função `plotSigwx3D` com extrusão de polígonos
5. Cores e transparência por tipo de fenômeno
6. Testar com dados reais do SIGWX

---

## Observações

- ol-cesium sincroniza a câmera automaticamente — zoom/pan no 2D reflete no 3D
- `flLower` vazio em ~70% das features — assumir FL000 é conservador e correto para CB
- SIGMET brasileiro (`/api/v1/sigmets`) também tem geometria e poderia ser plotado em 3D
  na mesma view — vale avaliar na implementação
- Performance: 411 features com polígonos extrudados é viável no Cesium sem otimização
