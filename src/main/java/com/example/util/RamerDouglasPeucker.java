package com.example.util;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class RamerDouglasPeucker {

    private static double perpendicularDistance(Point2D.Double pt, Point2D.Double lineStart, Point2D.Double lineEnd) {
        double dx = lineEnd.x - lineStart.x;
        double dy = lineEnd.y - lineStart.y;

        
        double mag = Math.sqrt(dx * dx + dy * dy);
        if (mag > 0.0) {
            dx /= mag;
            dy /= mag;
        }

        double pvx = pt.x - lineStart.x;
        double pvy = pt.y - lineStart.y;

       
        double pvdot = dx * pvx + dy * pvy;

       
        double ax = lineStart.x + pvdot * dx;
        double ay = lineStart.y + pvdot * dy;

      
        if (pvdot < 0.0) {
            ax = lineStart.x;
            ay = lineStart.y;
        } else if (pvdot > mag) {
            ax = lineEnd.x;
            ay = lineEnd.y;
        }

      
        double distDx = pt.x - ax;
        double distDy = pt.y - ay;
        return Math.sqrt(distDx * distDx + distDy * distDy);
    }

   
    public static List<Point2D.Double> simplify(List<Point2D.Double> pointList, double epsilon) {
        if (pointList == null || pointList.size() < 3) {
            return new ArrayList<>(pointList); 
        }

        
        double dmax = 0;
        int index = 0;
        int end = pointList.size() - 1;
        for (int i = 1; i < end; i++) {
            double d = perpendicularDistance(pointList.get(i), pointList.get(0), pointList.get(end));
            if (d > dmax) {
                index = i;
                dmax = d;
            }
        }

        List<Point2D.Double> resultList = new ArrayList<>();

      
        if (dmax > epsilon) {
          
            List<Point2D.Double> recResults1 = simplify(pointList.subList(0, index + 1), epsilon);
            List<Point2D.Double> recResults2 = simplify(pointList.subList(index, end + 1), epsilon);

         
            resultList.addAll(recResults1.subList(0, recResults1.size() - 1));
            resultList.addAll(recResults2);
        } else {
          
            resultList.add(pointList.get(0));
            resultList.add(pointList.get(end));
        }

        return resultList;
    }
}