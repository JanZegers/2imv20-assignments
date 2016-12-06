/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;

/**
 *
 * @author michel
 */
public class TransferFunction2DView extends javax.swing.JPanel {

    TransferFunction2DEditor ed;
    private final int DOTSIZE = 8;
    public Ellipse2D.Double baseControlPoint, radiusControlPoint, minControlPoint;
    boolean selectedBaseControlPoint, selectedRadiusControlPoint, selectedMinControlPoint;
    private double maxHistoMagnitude;
    
    /**
     * Creates new form TransferFunction2DView
     * @param ed
     */
    public TransferFunction2DView(TransferFunction2DEditor ed) {
        initComponents();
        
        this.ed = ed;
        selectedBaseControlPoint = false;
        selectedRadiusControlPoint = false;
        selectedMinControlPoint = false;
        addMouseMotionListener(new TriangleWidgetHandler());
        addMouseListener(new SelectionHandler());
    }
    
    @Override
    public void paintComponent(Graphics g) {

        Graphics2D g2 = (Graphics2D) g;

        int w = this.getWidth();
        int h = this.getHeight();
        g2.setColor(Color.white);
        g2.fillRect(0, 0, w, h);
        
        maxHistoMagnitude = ed.histogram[0];
        for (int i = 0; i < ed.histogram.length; i++) {
            maxHistoMagnitude = ed.histogram[i] > maxHistoMagnitude ? ed.histogram[i] : maxHistoMagnitude;
        }
        
        double binWidth = (double) w / (double) ed.xbins;
        double binHeight = (double) h / (double) ed.ybins;
        maxHistoMagnitude = Math.log(maxHistoMagnitude);
        
        for (int y = 0; y < ed.ybins; y++) {
            for (int x = 0; x < ed.xbins; x++) {
                if (ed.histogram[y * ed.xbins + x] > 0) {
                    int intensity = (int) Math.floor(255 * (1.0 - Math.log(ed.histogram[y * ed.xbins + x]) / maxHistoMagnitude));
                    g2.setColor(new Color(intensity, intensity, intensity));
                    g2.fill(new Rectangle2D.Double(x * binWidth, h - (y * binHeight), binWidth, binHeight));
                }
            }
        }
        
        double baseControlPointX = ed.triangleWidget.baseIntensity * binWidth;
        double baseControlPointY = h - ed.triangleWidget.baseGradient * h / ed.maxGradientMagnitude;
        double radiusControlPointX = baseControlPointX + (ed.triangleWidget.radius * binWidth * ed.maxGradientMagnitude);
        double radiusControlPointY = h - ed.triangleWidget.maxGradient * h / ed.maxGradientMagnitude;
        double radiusControlPointXEnd = baseControlPointX - (ed.triangleWidget.radius * binWidth * ed.maxGradientMagnitude);
        double minControlPointY = h - ed.triangleWidget.minGradient * h / ed.maxGradientMagnitude;
        double minControlPointX = baseControlPointX + (baseControlPointY - minControlPointY) / (baseControlPointY - radiusControlPointY) * (ed.triangleWidget.radius * binWidth * ed.maxGradientMagnitude);
        double minControlPointXEnd = baseControlPointX - (baseControlPointY - minControlPointY) / (baseControlPointY - radiusControlPointY) * (ed.triangleWidget.radius * binWidth * ed.maxGradientMagnitude); 
        
        g2.setColor(Color.black);
        baseControlPoint = new Ellipse2D.Double(baseControlPointX - DOTSIZE / 2, baseControlPointY - DOTSIZE, DOTSIZE, DOTSIZE);
        g2.fill(baseControlPoint);        
        g2.drawLine((int) baseControlPointX, (int) baseControlPointY, (int) radiusControlPointXEnd, (int) radiusControlPointY);
        g2.drawLine((int) baseControlPointX, (int) baseControlPointY, (int) radiusControlPointX, (int) radiusControlPointY);
        // Horizontal line max
        g2.drawLine((int) radiusControlPointXEnd, (int) radiusControlPointY, (int) radiusControlPointX, (int) radiusControlPointY);
        radiusControlPoint = new Ellipse2D.Double(radiusControlPointX - DOTSIZE / 2,  radiusControlPointY, DOTSIZE, DOTSIZE);
        g2.fill(radiusControlPoint);
        // Horizontal line min
        g2.drawLine((int) minControlPointXEnd, (int) minControlPointY, (int) minControlPointX, (int) minControlPointY);
        minControlPoint = new Ellipse2D.Double(minControlPointX - DOTSIZE / 2,  minControlPointY - DOTSIZE / 2, DOTSIZE, DOTSIZE);
        g2.fill(minControlPoint);
    }
    
    
    private class TriangleWidgetHandler extends MouseMotionAdapter {

        @Override
        public void mouseMoved(MouseEvent e) {
            if (baseControlPoint.contains(e.getPoint()) || radiusControlPoint.contains(e.getPoint()) || minControlPoint.contains(e.getPoint())) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else {
                setCursor(Cursor.getDefaultCursor());
            }
        }
        
        @Override
        public void mouseDragged(MouseEvent e) {
            if (selectedBaseControlPoint || selectedRadiusControlPoint || selectedMinControlPoint) {
                Point dragEnd = e.getPoint();
                
                int radiusControlPointYPos = (int) (getHeight() - ed.triangleWidget.maxGradient * getHeight() / ed.maxGradientMagnitude);
                int baseControlPointYPos = (int) (getHeight() - ed.triangleWidget.baseGradient * getHeight() / ed.maxGradientMagnitude);
                int minControlPointYPos = (int) (getHeight() - ed.triangleWidget.minGradient * getHeight() / ed.maxGradientMagnitude);
                if (selectedBaseControlPoint) {
                    if (dragEnd.x < 0) {
                        dragEnd.x = 0;
                    }
                    if (dragEnd.y > getHeight()) {
                        dragEnd.y = getHeight();
                    }
                    
                    if (dragEnd.y < minControlPointYPos + DOTSIZE / 2) {
                        dragEnd.y = minControlPointYPos + DOTSIZE / 2;
                    }
                } else if (selectedRadiusControlPoint) {
                    if (dragEnd.x - baseControlPoint.getCenterX() <= 0) {
                        dragEnd.x = (int) (baseControlPoint.getCenterX() + 1);
                    }
                    if (dragEnd.y < 0) {
                        dragEnd.y = 0;
                    }                    
                    if (dragEnd.y > minControlPointYPos - DOTSIZE / 2) {
                        dragEnd.y = minControlPointYPos - DOTSIZE / 2;
                    }  
                } else if (selectedMinControlPoint) {
                     // restrain to movement over line
                    dragEnd.setLocation(minControlPoint.getCenterX(), dragEnd.y);
                    
                    if (dragEnd.y > baseControlPointYPos - DOTSIZE / 2) {
                        dragEnd.y = baseControlPointYPos - DOTSIZE / 2;
                    }                    
                    if (dragEnd.y < radiusControlPointYPos + DOTSIZE / 2) {
                        dragEnd.y = radiusControlPointYPos + DOTSIZE / 2;
                    }
                }
                
                if (dragEnd.x > getWidth() - 1) {
                    dragEnd.x = getWidth() - 1;
                }                
                
                double w = getWidth();
                double h = getHeight();
                double binWidth = (double) w / (double) ed.xbins;
                if (selectedBaseControlPoint) {
                    ed.triangleWidget.baseIntensity = (short) (dragEnd.x / binWidth);
                    ed.triangleWidget.baseGradient = (h - dragEnd.y) / h * ed.maxGradientMagnitude;
                } else if (selectedRadiusControlPoint) {
                    ed.triangleWidget.radius = (dragEnd.x - (ed.triangleWidget.baseIntensity * binWidth))/(binWidth*ed.maxGradientMagnitude);
                    ed.triangleWidget.maxGradient = (h - dragEnd.y) / h * ed.maxGradientMagnitude;
                } else if (selectedMinControlPoint) {
                    ed.triangleWidget.minGradient = (h - dragEnd.y) / h * ed.maxGradientMagnitude;
                }
                ed.setSelectedInfo();
                
                repaint();
            } 
        }

    }
    
    
    private class SelectionHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            if (minControlPoint.contains(e.getPoint())) {
                selectedMinControlPoint = true;
            } else if (radiusControlPoint.contains(e.getPoint())) {
                selectedRadiusControlPoint = true;
            } else if (baseControlPoint.contains(e.getPoint())) {
                selectedBaseControlPoint = true;
            } else {
                selectedRadiusControlPoint = false;
                selectedBaseControlPoint = false;
                selectedMinControlPoint = false;
            }
        }
        
        @Override
        public void mouseReleased(MouseEvent e) {
            selectedRadiusControlPoint = false;
            selectedBaseControlPoint = false;
            selectedMinControlPoint = false;
            ed.changed();
            repaint();
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
