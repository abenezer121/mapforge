package com.example;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import com.example.util.osmparser;

public class Main extends JFrame {
    private mappanel mapPanel;
 
    private JTextField latTextField;
    private JTextField lonTextField;
    private JButton goToButton;

    public Main() {
        setTitle("OSM Map Viewer");
        setSize(1000, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); 

        mapPanel = new mappanel();
        add(mapPanel, BorderLayout.CENTER); 


        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        
        JButton zoomIn = new JButton("Zoom In");
        JButton zoomOut = new JButton("Zoom Out");
        JButton resetView = new JButton("Reset View");
        JButton openFile = new JButton("Open OSM File");

        zoomIn.addActionListener(e -> mapPanel.zoomIn());
        zoomOut.addActionListener(e -> mapPanel.zoomOut());
        resetView.addActionListener(e -> mapPanel.resetView());
        openFile.addActionListener(e -> openOSMFile());

  
        JLabel latLabel = new JLabel("Lat:");
        latTextField = new JTextField(8); 
        latTextField.setToolTipText("Enter latitude (-90 to 90)");

        JLabel lonLabel = new JLabel("Lon:");
        lonTextField = new JTextField(8); 
        lonTextField.setToolTipText("Enter longitude (-180 to 180)");

        goToButton = new JButton("Go");
        goToButton.setToolTipText("Center map on entered coordinates");
        goToButton.addActionListener(e -> goToCoordinates()); 

        controlPanel.add(openFile);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL)); 
        controlPanel.add(zoomIn);
        controlPanel.add(zoomOut);
        controlPanel.add(resetView);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL)); 
        controlPanel.add(latLabel);
        controlPanel.add(latTextField);
        controlPanel.add(lonLabel);
        controlPanel.add(lonTextField);
        controlPanel.add(goToButton);

        add(controlPanel, BorderLayout.SOUTH); 
    }

    private void openOSMFile() {
        JFileChooser fileChooser = new JFileChooser("."); 
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("OSM XML Files (*.osm)", "osm"));
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)); 
            try {
                System.out.println("Parsing file: " + selectedFile.getAbsolutePath());
                osmparser parser = new osmparser();
                parser.parse(selectedFile); 
                System.out.println("Parsing complete. Nodes: " + parser.getNodes().size() + ", Ways: " + parser.getWays().size());
                mapPanel.setData(parser.getNodes(), parser.getWays());
                System.out.println("Map data set and view reset.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error reading or parsing OSM file:\n" + ex.getMessage(),
                    "File Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            } finally {
                 setCursor(Cursor.getDefaultCursor()); 
            }
        }
    }

    
    private void goToCoordinates() {
        String latText = latTextField.getText().trim();
        String lonText = lonTextField.getText().trim();

        if (latText.isEmpty() || lonText.isEmpty()) {
             JOptionPane.showMessageDialog(this, "Please enter both latitude and longitude.", "Input Error", JOptionPane.WARNING_MESSAGE);
             return;
        }

        try {
            double lat = Double.parseDouble(latText);
            double lon = Double.parseDouble(lonText);

    
            if (lat < -90.0 || lat > 90.0) {
                JOptionPane.showMessageDialog(this, "Latitude must be between -90 and 90.", "Input Error", JOptionPane.ERROR_MESSAGE);
                latTextField.requestFocus(); 
                return;
            }
            if (lon < -180.0 || lon > 180.0) {
                JOptionPane.showMessageDialog(this, "Longitude must be between -180 and 180.", "Input Error", JOptionPane.ERROR_MESSAGE);
                lonTextField.requestFocus(); 
                return;
            }

          
            mapPanel.centerOn(lat, lon);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid number format.\nPlease enter valid decimal numbers for latitude and longitude.",
             "Input Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    public static void main(String[] args) {
        
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Couldn't set system look and feel.");
        }

        SwingUtilities.invokeLater(() -> {
            Main viewer = new Main(); // Use the correct class name
            viewer.setVisible(true);
        });
    }
}