package com.pocsigmet;

import org.springframework.stereotype.Component;

@Component
public class MongoMetarFallbackInjector {

    public MongoMetarFallbackInjector(MongoMetarFallback mongoMetarFallback) {
        RedisMetarCacheService.mongo = mongoMetarFallback;
    }
}
