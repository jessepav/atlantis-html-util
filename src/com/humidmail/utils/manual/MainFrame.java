package com.humidmail.utils.manual;

import com.jformdesigner.model.*;
import com.jformdesigner.runtime.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
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
    private JCheckBox removePageRefsCheckBox, removeTOCLeadersCheckBox, v2TOCFixCheckBox;
    private JButton processButton, helpButton;

    private JFileChooser chooser;

    private static Desktop desktop;

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
            helpButton = cr.getButton("helpButton");
            bodyWidthSpinner = cr.getSpinner("bodyWidthSpinner");
            indentSpinner = cr.getSpinner("indentSpinner");
            listSpacesSpinner = cr.getSpinner("listSpacesSpinner");
            thresholdSpinners = new JSpinner[3];
            sizeSpinners = new JSpinner[3];
            for (int i = 0; i < thresholdSpinners.length; i++) {
                thresholdSpinners[i] = cr.getSpinner("thresholdSpinner" + (i+1));
                sizeSpinners[i] = cr.getSpinner("sizeSpinner" + (i+1));
            }
            removePageRefsCheckBox = cr.getCheckBox("removePageRefsCheckBox");
            removeTOCLeadersCheckBox = cr.getCheckBox("removeTOCLeadersCheckBox");
            v2TOCFixCheckBox = cr.getCheckBox("v2TOCFixCheckBox");

            initializeComponents();

            JButton[] buttons = {inputBrowseButton, outputBrowseButton, processButton, helpButton};
            for (JButton b : buttons)
                b.addActionListener(this);

            frame.addWindowListener(new FrameListener());
            frame.setVisible(true);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(3);
        }
    }

    /**
     * Initialize our components based on the values of the static fields of FormatAtlantisHTML
     */
    private void initializeComponents() {
        bodyWidthSpinner.setValue(Integer.valueOf(Float.valueOf(FormatAtlantisHTML.bodyWidth).intValue()));
        indentSpinner.setValue(Integer.valueOf(Float.valueOf(FormatAtlantisHTML.indentAdjustment).intValue()));
        listSpacesSpinner.setValue(Integer.valueOf(Float.valueOf(FormatAtlantisHTML.listSpaces).intValue()));
        for (int i = 0; i < thresholdSpinners.length; i++) {
            thresholdSpinners[i].setValue(Integer.valueOf(Float.valueOf(FormatAtlantisHTML.fontSizeThreshold[i]).intValue()));
            sizeSpinners[i].setValue(Integer.valueOf(Float.valueOf(FormatAtlantisHTML.fontSizeAdjustment[i]).intValue()));
        }
        removePageRefsCheckBox.setSelected(FormatAtlantisHTML.removePageRefs);
        removeTOCLeadersCheckBox.setSelected(FormatAtlantisHTML.removeTOCLeaders);
        v2TOCFixCheckBox.setSelected(FormatAtlantisHTML.useV2TocPattern);
    }

    /**
     * Saves the values of our components into the static fields of FormatAtlantisHTML
     */
    private void saveComponentValues() {
        FormatAtlantisHTML.bodyWidth = ((Integer) bodyWidthSpinner.getValue()).intValue();
        FormatAtlantisHTML.indentAdjustment = ((Integer) indentSpinner.getValue()).floatValue();
        FormatAtlantisHTML.listSpaces = ((Integer) listSpacesSpinner.getValue()).intValue();
        for (int i = 0; i < thresholdSpinners.length; i++) {
            FormatAtlantisHTML.fontSizeThreshold[i] = ((Integer) thresholdSpinners[i].getValue()).floatValue();
            FormatAtlantisHTML.fontSizeAdjustment[i] = ((Integer) sizeSpinners[i].getValue()).floatValue();
        }
        FormatAtlantisHTML.removePageRefs = removePageRefsCheckBox.isSelected();
        FormatAtlantisHTML.removeTOCLeaders = removeTOCLeadersCheckBox.isSelected();
        FormatAtlantisHTML.useV2TocPattern = v2TOCFixCheckBox.isSelected();
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
                checkPropsFile(inputFileField.getText());
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
                saveComponentValues();
                try {
                    String msg = FormatAtlantisHTML.processFile(inputFile, outputFile);
                    JOptionPane.showMessageDialog(frame, msg);
                    if (msg.contains("written successfully"))
                        writePropsFile(inputFile);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "Error processing file:\n\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else if (source == helpButton) {
            if (desktop == null) {
                if (Desktop.isDesktopSupported()) {
                    desktop = Desktop.getDesktop();
                }
            }
            if (desktop != null) {
                try {
                    desktop.browse(new URI("https://jessepav.github.io/atlantis-html-util/users-guide.html"));
                } catch (IOException|URISyntaxException e1) {
                    JOptionPane.showMessageDialog(frame, "Unable to open a browser tab to display help");
                }
            }
        }
    }

    private void writePropsFile(String inputFile) throws IOException {
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
        props.setProperty("remove_page_refs", Boolean.toString(removePageRefsCheckBox.isSelected()));
        props.setProperty("remove_TOC_leaders", Boolean.toString(removeTOCLeadersCheckBox.isSelected()));
        props.setProperty("v2_TOC_fix", Boolean.toString(v2TOCFixCheckBox.isSelected()));
        props.setProperty("output_file", outputFileField.getText());
        int idx = inputFile.lastIndexOf('.');
        if (idx == -1)
            idx = inputFile.length();
        String propsFile = inputFile.substring(0, idx) + "-format.prefs";
        try (FileWriter w = new FileWriter(propsFile)) {
            props.store(w, "Atlantis HTML Util properties");
        }
    }

    private void checkPropsFile(String docPath) {
        int idx = docPath.lastIndexOf('.');
        if (idx == -1)
            idx = docPath.length();
        Path propsPath = Paths.get(docPath.substring(0, idx) + "-format.prefs");
        if (Files.exists(propsPath)) {
            if (JOptionPane.showConfirmDialog(frame,
                    "A saved settings file was found for this HTML document.\n\nLoad it?", "Load Settings",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
            {
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
                    removePageRefsCheckBox.setSelected(Boolean.parseBoolean(props.getProperty("remove_page_refs")));
                    removeTOCLeadersCheckBox.setSelected(Boolean.parseBoolean(props.getProperty("remove_TOC_leaders")));
                    v2TOCFixCheckBox.setSelected(Boolean.parseBoolean(props.getProperty("v2_TOC_fix")));
                    if (props.containsKey("output_file"))
                        outputFileField.setText(props.getProperty("output_file"));
                } catch (IOException ex) {
                    ex.printStackTrace();
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
