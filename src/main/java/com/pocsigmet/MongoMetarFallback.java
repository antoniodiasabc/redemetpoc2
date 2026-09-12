package com.pocsigmet;

import com.pocsigmet.mongo.MetarDocument;
import com.pocsigmet.mongo.MetarMongoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
public class MongoMetarFallback {

    private static final Logger log = LoggerFactory.getLogger(MongoMetarFallback.class);

    private final MongoTemplate mongoTemplate;

    public MongoMetarFallback(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void save(String icao, RedisMetarCacheService.CachedMetarData data) {
        try {
            String horario = extractHorario(data.metarText);
            if (horario == null) return;
            Query q = Query.query(Criteria.where("icao").is(icao).and("horario").is(horario));
            // se já existe, só atualiza se for COR ou AMD
            if (mongoTemplate.exists(q, MetarDocument.class)) {
                boolean isCorOrAmd = data.metarText != null &&
                    (data.metarText.contains(" COR ") || data.metarText.contains(" AMD "));
                if (!isCorOrAmd) return;
            }
            org.springframework.data.mongodb.core.query.Update upd = org.springframework.data.mongodb.core.query.Update
                .update("icao", icao)
                .set("horario", horario)
                .set("condition", data.condition)
                .set("metarText", data.metarText)
                .set("tafText", data.tafText)
                .set("hasAviso", data.hasAviso)
                .set("timestamp", System.currentTimeMillis())
                .set("createdAt", new java.util.Date());
            mongoTemplate.upsert(q, upd, MetarDocument.class);
        } catch (Exception e) {
            log.warn("Mongo save error {}: {}", icao, e.getMessage());
        }
    }

    private String extractHorario(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{6}Z)\\b").matcher(text);
        return m.find() ? m.group(1) : null;
    }

    public RedisMetarCacheService.CachedMetarData load(String icao) {
        try {
            Query q = Query.query(Criteria.where("icao").is(icao));
            q.with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(1);
            MetarDocument doc = mongoTemplate.findOne(q, MetarDocument.class);
            if (doc == null) return null;
            log.info("Mongo load {} ts={}", icao, doc.getTimestamp());
            RedisMetarCacheService.CachedMetarData data = new RedisMetarCacheService.CachedMetarData(
                doc.getCondition(), doc.getMetarText(), doc.getTafText(), doc.isHasAviso());
            data.timestamp = doc.getTimestamp();
            return data;
        } catch (Exception e) {
            log.warn("Mongo load error {}: {}", icao, e.getMessage());
            return null;
        }
    }
}
