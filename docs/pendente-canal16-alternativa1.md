# Pendente: Canal 16 Animação - Alternativa 1 (Nginx serve imagens)

## Mudanças necessárias

### 1. index.html — 2 linhas
- URL dos frames: `/canal16frame/` → `/data/`
- Polling: `120000` → `30000`

### 2. nginx.conf — já está pronto (etag on por padrão)

## Benefício
- Java para de servir imagens (zero CPU, zero threads)
- Nginx sendfile direto do disco
- Cache browser via ETag + 304
- Robusto: Java cai, imagens continuam sendo servidas
