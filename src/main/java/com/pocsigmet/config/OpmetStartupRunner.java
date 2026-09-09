package com.pocsigmet.config;

import com.pocsigmet.OpmetWebSocketHandler;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class OpmetStartupRunner implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        OpmetWebSocketHandler.initPermanentConnection();
    }
}
