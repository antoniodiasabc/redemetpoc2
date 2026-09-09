# Redis Pub/Sub para WebSocket multi-instância

## Problema

Com 3 instâncias (`app1`, `app2`, `app3`) e 2000 usuários conectados via SSE/WebSocket,
cada instância guarda suas sessões em memória local (`ConcurrentHashMap`).

Quando chega uma mensagem OPMET no `app1`, apenas os usuários conectados nele recebem.
Os outros 1333 usuários conectados em `app2` e `app3` não recebem nada.

```
METAR novo chega no app1
  → app1 notifica seus ~667 usuários  ✅
  → app2: nada                        ❌
  → app3: nada                        ❌
```

## Solução — Redis Pub/Sub

Quando qualquer instância recebe uma mensagem SSE do OPMET, publica no Redis channel.
Todas as instâncias assinam o channel e fazem broadcast para seus clientes locais.

```
METAR novo chega no app1
  → app1 publica no Redis channel "opmet:broadcast"
  → app1 recebe, notifica seus ~667 usuários   ✅
  → app2 recebe, notifica seus ~667 usuários   ✅
  → app3 recebe, notifica seus ~666 usuários   ✅
  → todos os 2000 recebem                      ✅
```

## Por que não pesa no Redis

- pub/sub não armazena — mensagem passa e some
- volume: ~5-20 mensagens/min em pico
- cada mensagem ~500 bytes
- 3 subscribers (app1, app2, app3)
- o cache de METARs já é muito mais pesado que o pub/sub

## Comparativo de abordagens

| Abordagem | Esforço | Sticky necessário | Falha de instância |
|---|---|---|---|
| `ip_hash` nginx | zero | sim (por IP) | usuário perde conexão |
| hash por cookie nginx | baixo (só nginx) | sim (por cookie) | usuário perde conexão |
| **Redis pub/sub** | médio (Java) | **não** | **transparente** |
| Hazelcast Topic | alto (migrar 103 refs Jedis) | não | transparente |

## Por que não Hazelcast

O projeto usa Jedis direto em 103 lugares — migrar para Hazelcast teria custo alto.
Redis já está no stack, pub/sub é a extensão natural.

## O que precisa mudar no código

Apenas `OpmetWebSocketHandler.java` — 2 pontos cirúrgicos:

### 1. No startup — assinar o channel Redis

```java
// ao iniciar, cada instância assina o channel
jedis.subscribe(new JedisPubSub() {
    public void onMessage(String channel, String message) {
        broadcastLocalOnly(message); // envia só para sessões locais
    }
}, "opmet:broadcast");
```

### 2. Em `broadcastMessage` — publicar no channel

```java
private static void broadcastMessage(String message) {
    broadcastLocalOnly(message);          // sessões locais
    redisPublish("opmet:broadcast", message); // outras instâncias
}
```

### Cuidado: não duplicar no leader

O `app1` (leader) não deve receber sua própria mensagem publicada e reenviar.
Solução: separar `broadcastLocalOnly` (só sessões locais) de `broadcastMessage` (local + publish).
O subscriber chama apenas `broadcastLocalOnly`.

## Teste de integração planejado

Teste com a aplicação rodando — sem mock, cenário real:

1. subir 2 instâncias: `docker compose up --scale app=2`
2. conectar cliente SSE diretamente na porta do `app1` (bypassando nginx)
3. conectar cliente SSE diretamente na porta do `app2`
4. simular mensagem OPMET chegando no `app1` via `/api/v1/opmet/simulate` (endpoint de teste)
5. verificar que **ambos** os clientes receberam a mensagem

### O que NÃO dá pra testar em unitário
- latência real do pub/sub
- comportamento com Redis caído no meio
- 2000 conexões simultâneas (precisa de k6/Gatling)

## Status

- [ ] Implementar pub/sub no `OpmetWebSocketHandler`
- [ ] Validar manualmente no browser com 2 instâncias
- [ ] Implementar teste de integração
- [ ] Avaliar teste de carga com k6
