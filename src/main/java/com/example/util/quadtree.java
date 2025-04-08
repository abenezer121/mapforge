package com.example.util;

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

public class quadtree {
    private static final int MAX_OBJECTS = 10;
    private static final int MAX_LEVELS = 5;
    
    private int level;
    private List<osmway> ways;
    private Rectangle2D.Double bounds;
    private quadtree[] nodes;
    
    public quadtree(int level, Rectangle2D.Double bounds) {
        this.level = level;
        this.bounds = bounds;
        this.ways = new ArrayList<>();
        this.nodes = new quadtree[4];
    }
    
    public void clear() {
        ways.clear();
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                nodes[i].clear();
                nodes[i] = null;
            }
        }
    }
    
    private void split() {
        double subWidth = bounds.width / 2;
        double subHeight = bounds.height / 2;
        double x = bounds.x;
        double y = bounds.y;
        
        nodes[0] = new quadtree(level + 1, new Rectangle2D.Double(x + subWidth, y, subWidth, subHeight));
        nodes[1] = new quadtree(level + 1, new Rectangle2D.Double(x, y, subWidth, subHeight));
        nodes[2] = new quadtree(level + 1, new Rectangle2D.Double(x, y + subHeight, subWidth, subHeight));
        nodes[3] = new quadtree(level + 1, new Rectangle2D.Double(x + subWidth, y + subHeight, subWidth, subHeight));
    }
    
    private int getIndex(osmway way, Map<Long, Point2D.Double> nodes) {
        if (way.nodeIds.isEmpty()) return -1;
        
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        
        for (Long nodeId : way.nodeIds) {
            Point2D.Double node = nodes.get(nodeId);
            if (node != null) {
                minX = Math.min(minX, node.x);
                maxX = Math.max(maxX, node.x);
                minY = Math.min(minY, node.y);
                maxY = Math.max(maxY, node.y);
            }
        }
        
        Rectangle2D.Double wayBounds = new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
        
        double verticalMidpoint = bounds.x + (bounds.width / 2);
        double horizontalMidpoint = bounds.y + (bounds.height / 2);
        
        boolean topQuadrant = wayBounds.y < horizontalMidpoint && wayBounds.y + wayBounds.height < horizontalMidpoint;
        boolean bottomQuadrant = wayBounds.y > horizontalMidpoint;
        
        if (wayBounds.x < verticalMidpoint && wayBounds.x + wayBounds.width < verticalMidpoint) {
            if (topQuadrant) return 1;
            if (bottomQuadrant) return 2;
        } else if (wayBounds.x > verticalMidpoint) {
            if (topQuadrant) return 0;
            if (bottomQuadrant) return 3;
        }
        
        return -1;
    }
    
    public void insert(osmway way, Map<Long, Point2D.Double> nodes) {
        if (this.nodes[0] != null) {
            int index = getIndex(way, nodes);
            if (index != -1) {
                this.nodes[index].insert(way, nodes);
                return;
            }
        }
        
        this.ways.add(way);
        
        if (this.ways.size() > MAX_OBJECTS && level < MAX_LEVELS) {
            if (this.nodes[0] == null) {
                split();
            }
            
            int i = 0;
            while (i < this.ways.size()) {
                int index = getIndex(this.ways.get(i), nodes);
                if (index != -1) {
                    this.nodes[index].insert(this.ways.remove(i), nodes);
                } else {
                    i++;
                }
            }
        }
    }
    
    public List<osmway> retrieve(List<osmway> returnWays, Rectangle2D.Double searchArea) {
        if (searchArea.intersects(bounds)) {
            if (nodes[0] != null) {
                for (quadtree node : nodes) {
                    node.retrieve(returnWays, searchArea);
                }
            }
            returnWays.addAll(ways);
        }
        return returnWays;
    }
}
