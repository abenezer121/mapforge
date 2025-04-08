package com.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;

import com.example.util.bounds;
import com.example.util.osmparser;
import com.example.util.osmway;
import com.example.*;

public  class mappanel extends JPanel {
    private Map<Long, Point2D.Double> nodes;
    private List<osmway> ways;
    private com.example.util.quadtree quadtree;
    private double scale = 10000.0;
    private static final double REFERENCE_SCALE = 10000.0;
    private static final double REFERENCE_ZOOM_LEVEL = 10.0;
    private static final double GOTO_COORDINATE_SCALE = 80000.0;
    private double centerLat = 0;
    private double centerLon = 0;
    private Point dragStart;
    private double tempCenterLat, tempCenterLon;
    private bounds bounds;
    
    public mappanel() {
        setBackground(Color.BLACK);
        nodes = new HashMap<>();
        ways = new ArrayList<>();
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart = e.getPoint();
                tempCenterLat = centerLat;
                tempCenterLon = centerLon;
            }
            
        });
        
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    Point dragEnd = e.getPoint();
                    int dx = dragEnd.x - dragStart.x;
                    int dy = dragEnd.y - dragStart.y;
                    
                    centerLon = tempCenterLon - dx / scale;
                    centerLat = tempCenterLat + dy / scale;
                    
                    repaint();
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }
        });
        
        addMouseWheelListener(e -> {
            if (e.getWheelRotation() < 0) {
                zoomIn(e.getPoint());
            } else {
                zoomOut(e.getPoint());
            }
        });
    }
    

    public double getCurrentZoomLevel() {
        if (scale <= 0) {
            return 0; 
        }
       
        return (Math.log(scale / REFERENCE_SCALE) / Math.log(2.0)) + REFERENCE_ZOOM_LEVEL;
    }


    public void centerOn(double lat, double lon) {
       
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
             System.err.println("Warning: Invalid coordinates passed to centerOn: lat=" + lat + ", lon=" + lon);
            
        }

        this.centerLat = lat;
        this.centerLon = lon;
        this.scale = GOTO_COORDINATE_SCALE; 

        System.out.println("Centering map on Lat: " + lat + ", Lon: " + lon + " with scale: " + this.scale); 
        repaint(); 
    }
    
    public void setData(Map<Long, Point2D.Double> nodes, List<osmway> ways) {
        this.nodes = nodes;
        this.ways = ways;
        calculateBounds();
        
        quadtree = new com.example.util.quadtree(0, new Rectangle2D.Double(
            bounds.minLon, bounds.minLat,
            bounds.maxLon - bounds.minLon,
            bounds.maxLat - bounds.minLat
        ));
        
        for (osmway way : ways) {
            quadtree.insert(way, nodes);
        }
        
        resetView();
    }
    
    private void calculateBounds() {
        if (nodes.isEmpty()) {
            bounds = new bounds(0, 0, 0, 0);
            return;
        }
        
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        
        for (Point2D.Double node : nodes.values()) {
            if (node.y < minLat) minLat = node.y;
            if (node.y > maxLat) maxLat = node.y;
            if (node.x < minLon) minLon = node.x;
            if (node.x > maxLon) maxLon = node.x;
        }
        
        bounds = new bounds(minLat, maxLat, minLon, maxLon);
    }
    
    public void zoomIn() {
        zoomIn(new Point(getWidth()/2, getHeight()/2));
    }
    
    public void zoomOut() {
        zoomOut(new Point(getWidth()/2, getHeight()/2));
    }
    
    public void zoomIn(Point zoomCenter) {
       
        double prevScale = scale;
        scale *= 1.2;
        adjustCenterAfterZoom(zoomCenter, prevScale);
        repaint();
    }
    
    public void zoomOut(Point zoomCenter) {
        double prevScale = scale;
        scale /= 1.2;
        adjustCenterAfterZoom(zoomCenter, prevScale);
        repaint();
    }
    
    private void adjustCenterAfterZoom(Point zoomCenter, double prevScale) {
        int w = getWidth();
        int h = getHeight();
        
        double mouseXInWorldBefore = (zoomCenter.x - w/2) / prevScale + centerLon;
        double mouseYInWorldBefore = (h/2 - zoomCenter.y) / prevScale + centerLat;
        
        double mouseXInWorldAfter = (zoomCenter.x - w/2) / scale + centerLon;
        double mouseYInWorldAfter = (h/2 - zoomCenter.y) / scale + centerLat;
        
        centerLon += (mouseXInWorldBefore - mouseXInWorldAfter);
        centerLat += (mouseYInWorldBefore - mouseYInWorldAfter);
    }
    
    public void resetView() {
        if (bounds != null) {
            centerLat = (bounds.minLat + bounds.maxLat) / 2;
            centerLon = (bounds.minLon + bounds.maxLon) / 2;
            
            double latSpan = bounds.maxLat - bounds.minLat;
            double lonSpan = bounds.maxLon - bounds.minLon;
            
            double scaleLat = getHeight() * 0.9 / latSpan;
            double scaleLon = getWidth() * 0.9 / lonSpan;
            
            scale = Math.min(scaleLat, scaleLon);
        } else {
            centerLat = 0;
            centerLon = 0;
            scale = 10000.0;
        }
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int w = getWidth();
        int h = getHeight();
        
        double visibleWidth = w / scale;
        double visibleHeight = h / scale;
        Rectangle2D.Double visibleBounds = new Rectangle2D.Double(
            centerLon - visibleWidth/2,
            centerLat - visibleHeight/2,
            visibleWidth,
            visibleHeight
        );
        
        List<osmway> visibleWays = new ArrayList<>();
        if (quadtree != null) {
            quadtree.retrieve(visibleWays, visibleBounds);
        }
        
        for (osmway way : visibleWays) {
            Color wayColor = Color.BLUE;
            if (way.tags.containsKey("highway")) {
                String highwayType = way.tags.get("highway");
                if (highwayType.equals("motorway") || highwayType.equals("trunk")) {
                    wayColor = new Color(200, 50, 50);
                } else if (highwayType.equals("primary")) {
                    wayColor = new Color(255, 150, 50);
                } else if (highwayType.equals("secondary")) {
                    wayColor = new Color(255, 255, 50);
                } else if(highwayType.equals("residential")){
                    if(getCurrentZoomLevel() < 12){
                        continue;
                    } 
                } 
                
                else {
                    wayColor = Color.WHITE;
                }
            } 
            
            g2d.setColor(wayColor);
            g2d.setStroke(new BasicStroke(way.tags.containsKey("highway") ? 3 : 1));
            
            int[] xPoints = new int[way.nodeIds.size()];
            int[] yPoints = new int[way.nodeIds.size()];
            int pointCount = 0;
           
                for (Long nodeId : way.nodeIds) {
                    Point2D.Double node = nodes.get(nodeId);
                    if (node != null) {
                        xPoints[pointCount] = (int) ((node.x - centerLon) * scale + w/2);
                        yPoints[pointCount] = (int) ((centerLat - node.y) * scale + h/2);
                        pointCount++;
                    }
                }
                
                if (pointCount > 1) {
                    g2d.drawPolyline(xPoints, yPoints, pointCount);
                }

                if(getCurrentZoomLevel() > 12){ 
                    int dotSize =  6;
                    for (int i = 0; i < pointCount; i++) {
                        g2d.fillOval(xPoints[i] - dotSize/2, yPoints[i] - dotSize/2, dotSize, dotSize);
                        g2d.setColor(Color.PINK);
                       
                    }

                }

               
           
            
           
        }
        
        g2d.setColor(Color.BLACK);
        g2d.drawString("Scale: 1:" + String.format("%.0f", scale), 10, 20);
        g2d.drawString(String.format("Center: %.6f, %.6f", centerLat, centerLon), 10, 40);
        g2d.drawString("Visible Ways: " + visibleWays.size(), 10, 60);
        g2d.drawString("Total Ways: " + ways.size(), 10, 80);
    }
}

