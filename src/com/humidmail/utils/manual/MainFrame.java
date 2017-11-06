package com.humidmail.utils.manual;

import com.jformdesigner.model.*;
import com.jformdesigner.runtime.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.nio.file.*;

public class MainFrame implements ActionListener, DocumentListener, FocusListener
{
    private JFrame frame;
    private JTextField inputFileField, outputFileField;
    private JButton inputBrowseButton, outputBrowseButton;
    private JSpinner bodyWidthSpinner, indentSpinner, listSpacesSpinner;
    private JSpinner[] thresholdSpinners, sizeSpinners;
    private JButton processButton;

    private JFileChooser chooser;

    private Document inputFieldDocument;
    private boolean inputFieldModified;

    /* There's a gnarly interaction that occurs when the inputFileField is modified (thus setting
       inputFieldModified to true) and then the processButton is clicked: the showConfirmDialog()
       call in checkPropsFile() pumps the AWT event queue and runs the actionPerformed() for
       processButton *before* the user clicks on anything in the confirm dialog. Therefore, we
       keep track of our call stack, as it were, and make sure that in this case the confirm dialog
       is clicked away before the processButton logic runs. */
    private boolean inFocusHandler, processRequested;

    // Don't prompt to load the same properties file twice in a row.
    private String lastPropFileName;

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

            lastPropFileName = "";
            inFocusHandler = processRequested = false;

            inputFileField.addFocusListener(this);
            inputFieldDocument = inputFileField.getDocument();
            inputFieldDocument.addDocumentListener(this);
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
                inputFieldModified = false;  // inhibit the focus handler
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
            if (inFocusHandler) {
                processRequested = true;
                return;
            }
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

    public void focusGained(FocusEvent e) { /* nothing */ }

    public void focusLost(FocusEvent e) {
        if (e.isTemporary())
            return;
        Component c = e.getComponent();
        if (c == inputFileField && inputFieldModified) {
            inFocusHandler = true;
            checkPropsFile(inputFileField.getText());
            inFocusHandler = false;
            if (processRequested) {
                processButton.doClick();
                processRequested = false;
            }
        }
    }

    public void insertUpdate(DocumentEvent e) {
        if (e.getDocument() == inputFieldDocument)
            inputFieldModified = true;
    }

    public void removeUpdate(DocumentEvent e) {
        if (e.getDocument() == inputFieldDocument)
            inputFieldModified = true;
    }

    public void changedUpdate(DocumentEvent e) {
        // plain text components do not fire this event
    }

    private void checkPropsFile(String docPath) {
        if (lastPropFileName.equals(docPath))
            return;

        int idx = docPath.lastIndexOf('.');
        if (idx == -1)
            idx = docPath.length();
        Path propsPath = Paths.get(docPath.substring(0, idx) + "-format.prefs");
        if (Files.exists(propsPath)) {
            lastPropFileName = docPath;
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
