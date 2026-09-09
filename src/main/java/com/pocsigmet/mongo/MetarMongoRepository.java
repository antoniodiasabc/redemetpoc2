package com.pocsigmet.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MetarMongoRepository extends MongoRepository<MetarDocument, String> {
    MetarDocument findByIcao(String icao);
}
