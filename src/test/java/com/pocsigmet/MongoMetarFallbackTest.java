package com.pocsigmet;

import com.pocsigmet.mongo.MetarDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class MongoMetarFallbackTest {

    private MongoTemplate mongoTemplate;
    private MongoMetarFallback fallback;

    @BeforeEach
    void setup() {
        mongoTemplate = mock(MongoTemplate.class);
        fallback = new MongoMetarFallback(mongoTemplate);
    }

    @Test
    void save_insertsWhenNoExistingDocument() {
        when(mongoTemplate.findOne(any(Query.class), eq(MetarDocument.class))).thenReturn(null);
        var data = new RedisMetarCacheService.CachedMetarData("VFR", "METAR SBSP 010000Z 00000KT 9999 FEW020 25/18 Q1013", "", false);

        fallback.save("SBSP", data);

        verify(mongoTemplate).insert(any(MetarDocument.class));
    }

    @Test
    void save_skipsInsertWhenMetarTextUnchanged() {
        MetarDocument existing = new MetarDocument("SBSP", "VFR", "METAR SBSP 010000Z 00000KT 9999 FEW020 25/18 Q1013", "", false, System.currentTimeMillis());
        when(mongoTemplate.findOne(any(Query.class), eq(MetarDocument.class))).thenReturn(existing);
        var data = new RedisMetarCacheService.CachedMetarData("VFR", "METAR SBSP 010000Z 00000KT 9999 FEW020 25/18 Q1013", "", false);

        fallback.save("SBSP", data);

        verify(mongoTemplate, never()).insert(any(MetarDocument.class));
    }

    @Test
    void save_insertsWhenMetarTextChanged() {
        MetarDocument existing = new MetarDocument("SBSP", "VFR", "METAR SBSP 010000Z 00000KT 9999 FEW020 25/18 Q1013", "", false, System.currentTimeMillis());
        when(mongoTemplate.findOne(any(Query.class), eq(MetarDocument.class))).thenReturn(existing);
        var data = new RedisMetarCacheService.CachedMetarData("IFR", "METAR SBSP 011000Z 18010KT 0800 FG OVC002 18/17 Q1010", "", false);

        fallback.save("SBSP", data);

        verify(mongoTemplate).insert(any(MetarDocument.class));
    }

    @Test
    void load_returnsNullWhenNoDocument() {
        when(mongoTemplate.findOne(any(Query.class), eq(MetarDocument.class))).thenReturn(null);

        assertNull(fallback.load("SBSP"));
    }

    @Test
    void load_returnsMostRecentDocument() {
        MetarDocument doc = new MetarDocument("SBSP", "VFR", "METAR SBSP 010000Z 00000KT 9999 FEW020 25/18 Q1013", "", false, 1000L);
        when(mongoTemplate.findOne(any(Query.class), eq(MetarDocument.class))).thenReturn(doc);

        var result = fallback.load("SBSP");

        assertNotNull(result);
        assertEquals("VFR", result.condition);
        assertEquals("METAR SBSP 010000Z 00000KT 9999 FEW020 25/18 Q1013", result.metarText);
        assertEquals(1000L, result.timestamp);
    }
}
