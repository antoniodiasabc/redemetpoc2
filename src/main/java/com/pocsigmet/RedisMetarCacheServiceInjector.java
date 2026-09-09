package com.pocsigmet;

import org.springframework.stereotype.Component;

@Component
public class RedisMetarCacheServiceInjector {

    public RedisMetarCacheServiceInjector(RedemetMetarClient metarClient, RedemetSigmetClient sigmetClient) {
        RedisMetarCacheService.METAR_CLIENT = metarClient;
        RedisMetarCacheService.SIGMET_CLIENT = sigmetClient;
    }
}
