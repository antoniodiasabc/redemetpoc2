package com.pocsigmet;

import com.pocsigmet.mongo.MetarDocument;
import com.pocsigmet.mongo.MetarMongoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
            Query q = Query.query(Criteria.where("icao").is(icao));
            Update u = new Update()
                .set("condition", data.condition)
                .set("metarText", data.metarText)
                .set("tafText", data.tafText)
                .set("hasAviso", data.hasAviso)
                .set("timestamp", data.timestamp);
            mongoTemplate.upsert(q, u, MetarDocument.class);
        } catch (Exception e) {
            log.warn("Mongo save error {}: {}", icao, e.getMessage());
        }
    }

    public RedisMetarCacheService.CachedMetarData load(String icao) {
        try {
            MetarDocument doc = mongoTemplate.findOne(
                Query.query(Criteria.where("icao").is(icao)), MetarDocument.class);
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
