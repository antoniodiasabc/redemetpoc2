# Animação de Radar — Pendente

## Backend (já pronto)
- `GET /api/radar/history/{sigla}` — retorna lista de até 10 URLs ordenada (mais antigo → mais recente)
- Redis persiste o histórico entre restarts
- Polling a cada 7 min — frame novo entra, mais antigo sai automaticamente

## Frontend (a implementar)
Quando o usuário ativar um radar:

1. Buscar `/api/radar/history/{sigla}` → lista de frames
2. Exibir animação ciclando os frames no layer OL (`ImageStatic`) com intervalo configurável (ex: 500ms)
3. Mostrar barra de progresso/timestamp do frame atual
4. Polling a cada 7 min em `/api/radar/history/{sigla}` — se lista mudou, atualizar frames sem intervenção do usuário
5. Botão play/pause na UI

## Observações
- Aguardar acúmulo de 10 frames (~63 min após deploy de 08/09/2026)
- Verificar com `curl http://localhost/api/radar/history/bv | python3 -c "import sys,json; print(len(json.load(sys.stdin)))"`
- Implementar junto animação 2D (OL) e 3D (Cesium) 
