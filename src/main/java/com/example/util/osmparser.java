package com.example.util;



import java.awt.geom.Point2D;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;

public  class osmparser extends DefaultHandler {
    private Map<Long, Point2D.Double> nodes = new HashMap<>();
    private List<osmway> ways = new ArrayList<>();
    private osmway currentWay;
    private Map<String, String> currentTags;
    
    public void parse(File file) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        saxParser.parse(file, this);
    }
    
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        switch (qName) {
            case "node":
                long id = Long.parseLong(attributes.getValue("id"));
                double lat = Double.parseDouble(attributes.getValue("lat"));
                double lon = Double.parseDouble(attributes.getValue("lon"));
                nodes.put(id, new Point2D.Double(lon, lat));
                break;
                
            case "way":
                currentWay = new osmway();
                currentWay.nodeIds = new ArrayList<>();
                currentTags = new HashMap<>();
                break;
                
            case "nd":
                if (currentWay != null) {
                    long ref = Long.parseLong(attributes.getValue("ref"));
                    currentWay.nodeIds.add(ref);
                }
                break;
                
            case "tag":
                if (currentTags != null) {
                    String k = attributes.getValue("k");
                    String v = attributes.getValue("v");
                    currentTags.put(k, v);
                }
                break;
        }
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) {
        if (qName.equals("way") && currentWay != null) {
            currentWay.tags = currentTags;
            ways.add(currentWay);
            currentWay = null;
            currentTags = null;
        }
    }
    
    public Map<Long, Point2D.Double> getNodes() {
        return nodes;
    }
    
    public List<osmway> getWays() {
        return ways;
    }
}
