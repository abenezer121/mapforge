package com.example;

import javax.swing.*;

import com.example.util.bounds;
import com.example.util.osmway;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class mappanel extends JPanel {
    private Map<Long, Point2D.Double> nodes;
    private List<osmway> ways;
    private double scale = 10000.0;
    private static final double REFERENCE_SCALE = 10000.0;
    private static final double REFERENCE_ZOOM_LEVEL = 10.0;
    private static final double GOTO_COORDINATE_SCALE = 80000.0;
    private double centerLat = 0;
    private double centerLon = 0;
    private Point dragStart;
    private double tempCenterLat, tempCenterLon;
    private bounds bounds;
    private osmway highlightedWay = null;
    private Long highlightedNode = null;
    private static final int HIT_BOX_SIZE = 10;
    
    // Optimization variables
    private BufferedImage offscreenBuffer;
    private boolean viewChanged = true;
    private Rectangle2D.Double currentViewBounds;
    private List<osmway> lastVisibleWays = new ArrayList<>();

    public mappanel() {
        setBackground(Color.BLACK);
        nodes = new HashMap<>();
        ways = new ArrayList<>();

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Point clickPoint = e.getPoint();
                if (!trySelectNodeOrWay(clickPoint)) {
                    dragStart = e.getPoint();
                    tempCenterLat = centerLat;
                    tempCenterLon = centerLon;
                }
                viewChanged = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (trySelectNodeOrWay(e.getPoint())) {
                    viewChanged = true;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    Point dragEnd = e.getPoint();
                    int dx = dragEnd.x - dragStart.x;
                    int dy = dragEnd.y - dragStart.y;

                    centerLon = tempCenterLon - dx / scale;
                    centerLat = tempCenterLat + dy / scale;

                    viewChanged = true;
                    repaint();
                }
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        addMouseWheelListener(e -> {
            if (e.getWheelRotation() < 0) {
                zoomIn(e.getPoint());
            } else {
                zoomOut(e.getPoint());
            }
            viewChanged = true;
        });
    }

    private boolean trySelectNodeOrWay(Point clickPoint) {
        int w = getWidth();
        int h = getHeight();

        // Try to select a node first (only check visible nodes)
        for (osmway way : lastVisibleWays) {
            for (Long nodeId : way.nodeIds) {
                Point2D.Double node = nodes.get(nodeId);
                if (node != null) {
                    int screenX = (int) ((node.x - centerLon) * scale + w/2);
                    int screenY = (int) ((centerLat - node.y) * scale + h/2);

                    if (Point2D.distance(clickPoint.x, clickPoint.y, screenX, screenY) <= HIT_BOX_SIZE) {
                        highlightedNode = nodeId;
                        highlightedWay = null;
                        repaint();
                        return true;
                    }
                }
            }
        }

        // If no node was clicked, try to select a way
        for (osmway way : lastVisibleWays) {
            if (isPointNearWay(clickPoint, way)) {
                highlightedWay = way;
                highlightedNode = null;
                repaint();
                return true;
            }
        }

        if (highlightedWay != null || highlightedNode != null) {
            highlightedWay = null;
            highlightedNode = null;
            repaint();
        }

        return false;
    }

    private boolean isPointNearWay(Point clickPoint, osmway way) {
        int w = getWidth();
        int h = getHeight();

        for (int i = 0; i < way.nodeIds.size() - 1; i++) {
            Point2D.Double node1 = nodes.get(way.nodeIds.get(i));
            Point2D.Double node2 = nodes.get(way.nodeIds.get(i + 1));

            if (node1 != null && node2 != null) {
                int x1 = (int) ((node1.x - centerLon) * scale + w/2);
                int y1 = (int) ((centerLat - node1.y) * scale + h/2);
                int x2 = (int) ((node2.x - centerLon) * scale + w/2);
                int y2 = (int) ((centerLat - node2.y) * scale + h/2);

                if (distanceToLineSegment(clickPoint, x1, y1, x2, y2) <= HIT_BOX_SIZE) {
                    return true;
                }
            }
        }
        return false;
    }

    private double distanceToLineSegment(Point p, int x1, int y1, int x2, int y2) {
        double A = p.x - x1;
        double B = p.y - y1;
        double C = x2 - x1;
        double D = y2 - y1;

        double dot = A * C + B * D;
        double len_sq = C * C + D * D;
        double param = -1;

        if (len_sq != 0) {
            param = dot / len_sq;
        }

        double xx, yy;

        if (param < 0) {
            xx = x1;
            yy = y1;
        } else if (param > 1) {
            xx = x2;
            yy = y2;
        } else {
            xx = x1 + param * C;
            yy = y1 + param * D;
        }

        return Point2D.distance(p.x, p.y, xx, yy);
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
        this.viewChanged = true;

        System.out.println("Centering map on Lat: " + lat + ", Lon: " + lon + " with scale: " + this.scale);
        repaint();
    }

    public void setData(Map<Long, Point2D.Double> nodes, List<osmway> ways) {
        this.nodes = nodes;
        this.ways = ways;
        calculateBounds();
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
        viewChanged = true;
        repaint();
    }

    public void zoomOut(Point zoomCenter) {
        double prevScale = scale;
        scale /= 1.2;
        adjustCenterAfterZoom(zoomCenter, prevScale);
        viewChanged = true;
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
        viewChanged = true;
        repaint();
    }

    private List<osmway> getVisibleWays() {
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

        currentViewBounds = visibleBounds;
        
        // Simple visibility check without quadtree
        return ways.stream()
            .filter(way -> isWayVisible(way, visibleBounds))
            .collect(Collectors.toList());
    }

    private boolean isWayVisible(osmway way, Rectangle2D.Double visibleBounds) {
        for (Long nodeId : way.nodeIds) {
            Point2D.Double node = nodes.get(nodeId);
            if (node != null && visibleBounds.contains(node.x, node.y)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        // Use offscreen buffer to avoid redrawing everything
        if (!viewChanged && offscreenBuffer != null) {
            g.drawImage(offscreenBuffer, 0, 0, null);
            return;
        }
        
        // Create new offscreen buffer if needed
        if (offscreenBuffer == null || 
            offscreenBuffer.getWidth() != getWidth() || 
            offscreenBuffer.getHeight() != getHeight()) {
            offscreenBuffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        }
        
        Graphics2D bufferGraphics = (Graphics2D) offscreenBuffer.getGraphics();
        
        // Clear buffer
        bufferGraphics.setColor(Color.BLACK);
        bufferGraphics.fillRect(0, 0, getWidth(), getHeight());
        bufferGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                                     RenderingHints.VALUE_ANTIALIAS_ON);

        // Get visible ways
        lastVisibleWays = getVisibleWays();
        
        // Render ways by type for better batching
        renderHighways(bufferGraphics);
        renderOtherWays(bufferGraphics);
        
        // Render highlights
        renderHighlights(bufferGraphics);
        
        // Render UI text
        renderUI(bufferGraphics);
        
        // Draw the buffer
        g.drawImage(offscreenBuffer, 0, 0, null);
        
        viewChanged = false;
    }

    private void renderHighways(Graphics2D g2d) {
        int w = getWidth();
        int h = getHeight();
        double zoomLevel = getCurrentZoomLevel();

        for (osmway way : lastVisibleWays) {
            if (!way.tags.containsKey("highway")) continue;
            
            String highwayType = way.tags.get("highway");
            Color wayColor;
            int strokeWidth;
            
            // Determine style based on highway type
            if (highwayType.equals("motorway") || highwayType.equals("trunk")) {
                wayColor = new Color(200, 50, 50);
                strokeWidth = 4;
            } else if (highwayType.equals("primary")) {
                wayColor = new Color(255, 150, 50);
                strokeWidth = 3;
            } else if (highwayType.equals("secondary")) {
                wayColor = new Color(255, 255, 50);
                strokeWidth = 2;
            } else if (highwayType.equals("residential")) {
                if (zoomLevel < 10) continue;
                wayColor = Color.WHITE;
                strokeWidth = 1;
            } else {
                wayColor = Color.WHITE;
                strokeWidth = 1;
            }
            
            g2d.setColor(wayColor);
            g2d.setStroke(new BasicStroke(strokeWidth));
            
            drawWay(g2d, way, w, h);
        }
    }

    private void renderOtherWays(Graphics2D g2d) {
        int w = getWidth();
        int h = getHeight();

        for (osmway way : lastVisibleWays) {
            if (way.tags.containsKey("highway")) continue;
            
            g2d.setColor(Color.BLUE);
            g2d.setStroke(new BasicStroke(1));
            drawWay(g2d, way, w, h);
        }
    }

    private void drawWay(Graphics2D g2d, osmway way, int w, int h) {
        GeneralPath path = new GeneralPath();
        boolean first = true;
        
        for (Long nodeId : way.nodeIds) {
            Point2D.Double node = nodes.get(nodeId);
            if (node != null) {
                int x = (int) ((node.x - centerLon) * scale + w/2);
                int y = (int) ((centerLat - node.y) * scale + h/2);
                
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            }
        }
        g2d.draw(path);
    }

    private void renderHighlights(Graphics2D g2d) {
        int w = getWidth();
        int h = getHeight();
        double zoomLevel = getCurrentZoomLevel();

        // Highlight way
        if (highlightedWay != null) {
            g2d.setColor(Color.YELLOW);
            g2d.setStroke(new BasicStroke(6));
            drawWay(g2d, highlightedWay, w, h);
        }

        // Highlight nodes at high zoom levels
        if (zoomLevel > 12) {
            int dotSize = 6;
            for (osmway way : lastVisibleWays) {
                if (highlightedWay != null && way != highlightedWay) continue;
                
                for (Long nodeId : way.nodeIds) {
                    Point2D.Double node = nodes.get(nodeId);
                    if (node != null) {
                        int x = (int) ((node.x - centerLon) * scale + w/2);
                        int y = (int) ((centerLat - node.y) * scale + h/2);
                        
                        if (nodeId.equals(highlightedNode)) {
                            g2d.setColor(Color.WHITE);
                            g2d.fillOval(x - (dotSize+2)/2, y - (dotSize+2)/2, dotSize+2, dotSize+2);
                            g2d.setColor(Color.RED);
                        } else {
                            g2d.setColor(Color.PINK);
                        }
                        g2d.fillOval(x - dotSize/2, y - dotSize/2, dotSize, dotSize);
                    }
                }
            }
        }
    }

    private void renderUI(Graphics2D g2d) {
        g2d.setColor(Color.WHITE);
        g2d.drawString("Scale: 1:" + String.format("%.0f", scale), 10, 20);
        g2d.drawString(String.format("Center: %.6f, %.6f", centerLat, centerLon), 10, 40);
        g2d.drawString("Visible Ways: " + lastVisibleWays.size(), 10, 60);
        g2d.drawString("Total Ways: " + ways.size(), 10, 80);
    }
}