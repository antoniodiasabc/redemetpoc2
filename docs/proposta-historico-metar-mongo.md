# Proposta: Histórico 12h de METARs no MongoDB

## Motivação
Atualmente o Mongo guarda apenas o último METAR por aeródromo (upsert por ICAO).
Para comparações temporais (ex: variação de temperatura, tendência de pressão) é necessário histórico.

## Mudanças propostas

### 1. MetarDocument.java
- Remove `@Indexed(unique=true)` do `icao`
- Adiciona campo `createdAt` com TTL index de 12h (43200s) para expiração automática

```java
@Indexed
private String icao;

@Indexed(expireAfterSeconds = 43200) // 12h TTL
private java.util.Date createdAt;
```

### 2. MetarMongoRepository.java
- Adiciona queries de histórico

```java
java.util.List<MetarDocument> findByIcaoOrderByTimestampDesc(String icao);
java.util.List<MetarDocument> findByHasAvisoTrueOrderByTimestampDesc();
```

### 3. MongoMetarFallback.java
- Troca `upsert` por `insert` condicional — só insere se o `metarText` mudou
- `load()` passa a buscar o documento mais recente por timestamp

```java
// save — só insere se mudou
MetarDocument last = mongoTemplate.findOne(q, MetarDocument.class);
if (last != null && last.getMetarText().equals(data.metarText)) return;
mongoTemplate.insert(new MetarDocument(...));

// load — mais recente
q.with(Sort.by(Sort.Direction.DESC, "timestamp"));
MetarDocument doc = mongoTemplate.findOne(q, MetarDocument.class);
```

## Impacto estimado
- METAR a cada ~30min × 97 aeródromos × 12h = ~2300 documentos máximo
- TTL index apaga automaticamente — zero manutenção
- `load()` sempre retorna o mais recente — comportamento idêntico ao atual

## O que NÃO muda
- Redis, cache local, endpoints, front-end

## Casos de uso habilitados após a mudança
- Variação de temperatura entre observações consecutivas
- Tendência de pressão (subindo/caindo)
- Histórico de SPECIs por aeródromo
- Histórico de avisos de aeródromo (`hasAviso=true`)
- Base para filtros temporais via JSON no `MetarFiltroHandler`
