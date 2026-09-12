package com.pocsigmet.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface MetarMongoRepository extends MongoRepository<MetarDocument, String> {
    MetarDocument findByIcao(String icao);
    List<MetarDocument> findByIcaoOrderByTimestampDesc(String icao);
    List<MetarDocument> findByHasAvisoTrueOrderByTimestampDesc();
}
