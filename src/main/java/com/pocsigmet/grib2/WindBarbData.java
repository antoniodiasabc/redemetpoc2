package com.pocsigmet.grib2;

public class WindBarbData {
    private double lat;
    private double lon;
    private double direction;
    private double speed;
    private double u;
    private double v;
    private String level;
    
    // Construtor vazio
    public WindBarbData() {}
    
    // Construtor completo
    public WindBarbData(double lat, double lon, double direction, double speed, double u, double v, String level) {
        this.lat = lat;
        this.lon = lon;
        this.direction = direction;
        this.speed = speed;
        this.u = u;
        this.v = v;
        this.level = level;
    }
    
    // Getters and Setters
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    
    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }
    
    public double getDirection() { return direction; }
    public void setDirection(double direction) { this.direction = direction; }
    
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
    
    public double getU() { return u; }
    public void setU(double u) { this.u = u; }
    
    public double getV() { return v; }
    public void setV(double v) { this.v = v; }
    
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
}
