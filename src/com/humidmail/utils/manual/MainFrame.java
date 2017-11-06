package com.humidmail.utils.manual;

import com.jformdesigner.model.*;
import com.jformdesigner.runtime.*;

import javax.swing.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.nio.file.*;

public class MainFrame implements ActionListener
{
    private JFrame frame;
    private JTextField inputFileField, outputFileField;
    private JButton inputBrowseButton, outputBrowseButton;
    private JSpinner bodyWidthSpinner, indentSpinner, listSpacesSpinner;
    private JSpinner[] thresholdSpinners, sizeSpinners;
    private JButton processButton;

    private JFileChooser chooser;

    MainFrame() {
        try {
            FormModel model = FormLoader.load("com/humidmail/utils/manual/MainFrame.jfd");
            FormCreator cr = new FormCreator(model);

            frame = (JFrame) cr.createWindow(null);
            inputFileField = cr.getTextField("inputFileField");
            outputFileField = cr.getTextField("outputFileField");
            inputBrowseButton = cr.getButton("inputBrowseButton");
            outputBrowseButton = cr.getButton("outputBrowseButton");
            processButton = cr.getButton("processButton");
            bodyWidthSpinner = cr.getSpinner("bodyWidthSpinner");
            indentSpinner = cr.getSpinner("indentSpinner");
            listSpacesSpinner = cr.getSpinner("listSpacesSpinner");
            thresholdSpinners = new JSpinner[3];
            sizeSpinners = new JSpinner[3];
            for (int i = 0; i < thresholdSpinners.length; i++) {
                thresholdSpinners[i] = cr.getSpinner("thresholdSpinner" + (i+1));
                sizeSpinners[i] = cr.getSpinner("sizeSpinner" + (i+1));
            }

            // Pull the current values out of the static fields of FormatAtlantisHTML
            bodyWidthSpinner.setValue(Integer.valueOf(Float.valueOf(FormatAtlantisHTML.bodyWidth).intValue()));
            indentSpinner.setValue(Integer.valueOf(Float.valueOf(FormatAtlantisHTML.indentAdjustment).intValue()));
            listSpacesSpinner.setValue(Integer.valueOf(Float.valueOf(FormatAtlantisHTML.listSpaces).intValue()));
            for (int i = 0; i < thresholdSpinners.length; i++) {
                thresholdSpinners[i].setValue(Integer.valueOf(Float.valueOf(FormatAtlantisHTML.fontSizeThreshold[i]).intValue()));
                sizeSpinners[i].setValue(Integer.valueOf(Float.valueOf(FormatAtlantisHTML.fontSizeAdjustment[i]).intValue()));
            }

            JButton[] buttons = {inputBrowseButton, outputBrowseButton, processButton};
            for (JButton b : buttons)
                b.addActionListener(this);
                
            // TODO: attach a focus listener to inputFileField, and on focus-lost, check for a property file to load
            frame.addWindowListener(new FrameListener());
            frame.setVisible(true);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(3);
        }
    }

    private void close() {
        frame.setVisible(false);
        frame.dispose();
    }

    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == inputBrowseButton) {
            if (chooser == null)
                chooser = new JFileChooser();
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                Path p = chooser.getSelectedFile().toPath();
                inputFileField.setText(p.toString());
                String name = p.getFileName().toString();
                int idx = name.lastIndexOf('.');
                if (idx != -1)
                    name = name.substring(0, idx);
                outputFileField.setText(p.resolveSibling(name + "-out.html").toString());

                Path propsPath = p.resolveSibling(name + "-format.prefs");
                if (Files.exists(propsPath)) {
                    try (FileReader r = new FileReader(propsPath.toFile())) {
                        Properties props = new Properties();
                        props.load(r);
                        bodyWidthSpinner.setValue(Integer.valueOf(props.getProperty("body_width", "750")));
                        indentSpinner.setValue(Integer.valueOf(props.getProperty("indent", "0")));
                        listSpacesSpinner.setValue(Integer.valueOf(props.getProperty("list_spaces", "0")));
                        for (int i = 0; i < thresholdSpinners.length; i++) {
                            String key1 = "font_threshold" + (i+1);
                            String key2 = "font_size" + (i+1);
                            if (props.containsKey(key1))
                                thresholdSpinners[i].setValue(Integer.valueOf(props.getProperty(key1)));
                            if (props.containsKey(key2))
                                sizeSpinners[i].setValue(Integer.valueOf(props.getProperty(key2)));
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } else if (source == outputBrowseButton) {
            if (chooser == null)
                chooser = new JFileChooser();
            if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                Path p = chooser.getSelectedFile().toPath();
                outputFileField.setText(p.toString());
            }
        } else if (source == processButton) {
            String inputFile = inputFileField.getText();
            String outputFile = outputFileField.getText();
            if (!inputFile.isEmpty() && !outputFile.isEmpty()) {
                FormatAtlantisHTML.bodyWidth = ((Integer) bodyWidthSpinner.getValue()).intValue();
                FormatAtlantisHTML.indentAdjustment = ((Integer) indentSpinner.getValue()).floatValue();
                FormatAtlantisHTML.listSpaces = ((Integer) listSpacesSpinner.getValue()).intValue();
                for (int i = 0; i < thresholdSpinners.length; i++) {
                    FormatAtlantisHTML.fontSizeThreshold[i] = ((Integer) thresholdSpinners[i].getValue()).floatValue();
                    FormatAtlantisHTML.fontSizeAdjustment[i] = ((Integer) sizeSpinners[i].getValue()).floatValue();
                }
                try {
                    JOptionPane.showMessageDialog(frame, FormatAtlantisHTML.processFile(inputFile, outputFile));

                    // And now save our values to a properties file
                    Properties props = new Properties();
                    props.setProperty("body_width", bodyWidthSpinner.getValue().toString());
                    props.setProperty("indent", indentSpinner.getValue().toString());
                    props.setProperty("list_spaces", listSpacesSpinner.getValue().toString());
                    for (int i = 0; i < thresholdSpinners.length; i++) {
                        String key1 = "font_threshold" + (i+1);
                        String key2 = "font_size" + (i+1);
                        props.setProperty(key1, thresholdSpinners[i].getValue().toString());
                        props.setProperty(key2, sizeSpinners[i].getValue().toString());
                    }
                    String propsFile = inputFile;
                    int idx = propsFile.lastIndexOf('.');
                    if (idx != -1)
                        propsFile = propsFile.substring(0, idx);
                    propsFile += "-format.prefs";
                    try (FileWriter w = new FileWriter(propsFile)) {
                        props.store(w, "Atlantis HTML Util properties");
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "Error processing file:\n\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private class FrameListener extends WindowAdapter
    {
        public void windowClosing(WindowEvent e) {
            close();
        }
    }
}
