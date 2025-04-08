package com.example.util;

 
public  class bounds {
    public double minLat, maxLat;
    public double minLon;
    public double maxLon;
    
    public bounds(double minLat, double maxLat, double minLon, double maxLon) {
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLon = minLon;
        this.maxLon = maxLon;
    }
}