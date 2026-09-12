package com.pocsigmet.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;

@Document(collection = "metars")
public class MetarDocument {

    @Id
    private String id;

    @Indexed
    private String icao;

    @Indexed(expireAfterSeconds = 43200)
    private Date createdAt;

    private String condition;
    private String metarText;
    private String tafText;
    private boolean hasAviso;
    private long timestamp;

    public MetarDocument() {}

    public MetarDocument(String icao, String condition, String metarText, String tafText, boolean hasAviso, long timestamp) {
        this.icao = icao;
        this.condition = condition;
        this.metarText = metarText;
        this.tafText = tafText;
        this.hasAviso = hasAviso;
        this.timestamp = timestamp;
        this.createdAt = new Date();
    }

    public String getIcao() { return icao; }
    public String getCondition() { return condition; }
    public String getMetarText() { return metarText; }
    public String getTafText() { return tafText; }
    public boolean isHasAviso() { return hasAviso; }
    public long getTimestamp() { return timestamp; }
    public Date getCreatedAt() { return createdAt; }
}
