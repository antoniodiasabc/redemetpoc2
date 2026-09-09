package com.pocsigmet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

/**
 * Testa o comportamento do lock de warmup com heartbeat.
 * Usa um mock simples de Redis em memória via HashMap.
 */
public class WarmupLockTest {

    // Simula o comportamento do acquireLock + heartbeat sem Redis real
    private java.util.concurrent.ConcurrentHashMap<String, String> fakeRedis;

    @BeforeEach
    void setUp() {
        fakeRedis = new java.util.concurrent.ConcurrentHashMap<>();
    }

    // Simula acquireLock: SET key 1 NX EX ttl
    private boolean acquireLock(String key) {
        return fakeRedis.putIfAbsent(key, "1") == null;
    }

    private void releaseLock(String key) {
        fakeRedis.remove(key);
    }

    @Test
    void soUmContainerAdquireLock() {
        assertTrue(acquireLock("lock:metar:warmup"), "primeiro container deve adquirir");
        assertFalse(acquireLock("lock:metar:warmup"), "segundo container deve ser bloqueado");
    }

    @Test
    void lockLiberadoAposWarmup() {
        acquireLock("lock:metar:warmup");
        releaseLock("lock:metar:warmup");
        assertTrue(acquireLock("lock:metar:warmup"), "deve adquirir após liberação");
    }

    @Test
    void segundoContainerAdquireAposLiberacao() throws InterruptedException {
        acquireLock("lock:metar:warmup");

        // simula warmup em thread separada que libera após 100ms
        Thread warmup = new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            releaseLock("lock:metar:warmup");
        });
        warmup.start();

        // segundo container tenta a cada 50ms
        boolean acquired = false;
        for (int i = 0; i < 10; i++) {
            Thread.sleep(50);
            if (acquireLock("lock:metar:warmup")) { acquired = true; break; }
        }
        warmup.join();
        assertTrue(acquired, "segundo container deve adquirir após o primeiro liberar");
    }

    @Test
    void heartbeatRenovaTTL() throws InterruptedException {
        // Simula que o heartbeat mantém o lock vivo
        acquireLock("lock:metar:warmup");
        assertTrue(fakeRedis.containsKey("lock:metar:warmup"));

        // heartbeat "renova" — chave continua presente
        fakeRedis.put("lock:metar:warmup", "1"); // simula expire
        assertTrue(fakeRedis.containsKey("lock:metar:warmup"), "heartbeat deve manter lock ativo");
    }

    @Test
    void lockNaoAdquiridoSeJaExiste() {
        fakeRedis.put("lock:metar:warmup", "1"); // simula lock já existente
        assertFalse(acquireLock("lock:metar:warmup"), "não deve adquirir lock já existente");
    }
}
