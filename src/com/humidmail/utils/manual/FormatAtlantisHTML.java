package com.humidmail.utils.manual;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import java.nio.charset.Charset;

import com.sanityinc.jargs.CmdLineParser;
import com.sanityinc.jargs.CmdLineParser.Option;

import javax.swing.*;

public class FormatAtlantisHTML
{
    static int bodyWidth;
    static float[] fontSizeAdjustment, fontSizeThreshold;
    static float indentAdjustment;
    static int listSpaces;
    static boolean removeTOCLeaders, removePageRefs;

    private static String usageText;

    public static void main(String[] args) throws IOException {
        CmdLineParser parser = new CmdLineParser();
        Option<Integer> widthOpt = parser.addIntegerOption('w', "width");
        Option<Integer> listOpt = parser.addIntegerOption('l', "listspaces");
        Option<String> sizeOpt = parser.addStringOption('f', "fontsize");
        Option<String> thresholdOpt = parser.addStringOption('t', "threshold");
        Option<Double> indentOpt = parser.addDoubleOption('i', "indent");
        Option<Boolean> pageRefsOpt = parser.addBooleanOption('p', "keep_page_refs");
        Option<Boolean> tocOpt = parser.addBooleanOption('t', "keep_TOC_leaders");
        Option<Boolean> helpOpt = parser.addBooleanOption('h', "help");
        Option<Boolean> guiOpt = parser.addBooleanOption("gui");

        try {
            parser.parse(args);
        } catch (CmdLineParser.OptionException e) {
            println(e.getMessage());
            println(getUsageText());
            System.exit(2);
        }
        String[] files = parser.getRemainingArgs();
        if (parser.getOptionValue(helpOpt, Boolean.FALSE)) {
            println(getUsageText());
            System.exit(0);
        }
        bodyWidth = parser.getOptionValue(widthOpt, Integer.valueOf(750));
        fontSizeThreshold = new float[] {10f, 17f, 20f};
        fontSizeAdjustment = new float[] {4.0f,3.0f,21.0f};
        setFloatArray(fontSizeAdjustment, parser.getOptionValue(sizeOpt));
        setFloatArray(fontSizeThreshold, parser.getOptionValue(thresholdOpt));
        indentAdjustment = parser.getOptionValue(indentOpt, 0.0).floatValue();
        listSpaces = parser.getOptionValue(listOpt, 0);
        removeTOCLeaders = !parser.getOptionValue(tocOpt, Boolean.FALSE).booleanValue();
        removePageRefs = !parser.getOptionValue(pageRefsOpt, Boolean.FALSE).booleanValue();

        if (parser.getOptionValue(guiOpt, Boolean.FALSE)) {
            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    try {
                        javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
                    } catch (Exception ex) { /* It's okay if setting the L&F failed.*/ }
                    new MainFrame();
                }
            });
        } else { // command-line mode
            if (files.length == 0 || files.length % 2 != 0) {
                println(getUsageText());
                System.exit(1);
            }
            println("Body Width: " + bodyWidth + "px");
            println("Font Size Adjustment: " + Arrays.toString(fontSizeAdjustment));
            println("Font Size Threshold: " + Arrays.toString(fontSizeThreshold));
            println("Indent adjustment: " + indentAdjustment);
            println("List separator spaces: " + listSpaces);
            println("Remove TOC Leaders: " + removeTOCLeaders);
            println("Remove Page Refs: " + removePageRefs);
            for (int i = 0; i < files.length; i += 2) {
                println("Processing: " + files[i] + " -> " + files[i+1]);
                println(processFile(files[i], files[i+1]));
            }
        }
    }

    private static void setFloatArray(float[] fa, String s) {
        if (s != null) {
            String[] sa = s.split(",");
            for (int i = 0; i < Math.min(sa.length, fa.length); i++) {
                try {
                    fa[i] = Float.parseFloat(sa[i]);
                } catch (NumberFormatException ex) {
                    println(sa[i] + " is not a valid float value");
                }
            }
        }
    }

    static String getUsageText() throws UnsupportedEncodingException {
        if (usageText == null) {
            InputStream in = FormatAtlantisHTML.class.getResourceAsStream("/resources/usage.txt");
            Reader reader = new InputStreamReader(in, "UTF-8");
            usageText = slurpReaderText(reader, 256);
        }
        return usageText;
    }

    private static final Pattern fontSizePattern = Pattern.compile("font-size\\s{0,2}:\\s{0,2}(\\d[\\d\\.]*)pt");
    private static final Pattern TOCDotPattern = Pattern.compile("\\.{15,}\\d+");
    private static final Pattern pageReferencePattern = Pattern.compile("\\s?\\(see p\\.\\d+\\)");
    private static final Pattern indentPattern =
        Pattern.compile("(text-indent|margin-left)\\s{0,2}:\\s{0,2}(-?\\d+(?:\\.\\d+)?)(pt|in)", Pattern.CASE_INSENSITIVE);
    private static final Pattern listSpacesPattern = Pattern.compile("(?:&nbsp;|<p>)\\d+\\.(?=\\p{IsAlphabetic})");

    static String processFile(String inputFile, String outputFile) throws IOException {
        Path inputPath = Paths.get(inputFile);
        Path outputPath = Paths.get(outputFile);
        if (!Files.exists(inputPath)) {
            return "   " + inputFile + " doesn't exist.";
        }
        List<String> lines = Files.readAllLines(inputPath, Charset.defaultCharset());
        String line;
        StringBuffer sb = new StringBuffer(1024);
        for (int i = 0; i < lines.size(); lines.set(i++, line)) {
            line = lines.get(i);

            // Check if this file has already been processed; if so, skip it
            if (line.contains("content=\"FormatAtlantisHTML\"")) {
                return "   " + inputFile + " has already been processed; skipping.";
            }

            // Add a meta tag indicating the file has been processed
            if (line.contains("<meta name=Generator") || line.contains("<meta name=\"Generator\"")) {
                line = line +
                    "\n<meta name=\"Processor\" content=\"FormatAtlantisHTML\">";
                continue;
            }

            // Fix up the font size
            Matcher m = fontSizePattern.matcher(line);
            sb.setLength(0);
            while (m.find()) {
                float pt = Float.parseFloat(m.group(1));
                if (pt <= fontSizeThreshold[0])
                    pt += fontSizeAdjustment[0];
                else if (pt <= fontSizeThreshold[1])
                    pt += fontSizeAdjustment[1];
                else if (pt <= fontSizeThreshold[2])
                    pt = fontSizeAdjustment[2];
                String s = Float.toString(pt);
                int idx = s.indexOf('.');
                if (idx != -1)
                    s = s.substring(0, idx + 2);
                m.appendReplacement(sb, "font-size:" + s + "pt");
            }
            m.appendTail(sb);
            line = sb.toString();

            // Adjust indents
            if (indentAdjustment != 0.0) {
                m = indentPattern.matcher(line);
                sb.setLength(0);
                while (m.find()) {
                    String prop = m.group(1);
                    float val = Float.parseFloat(m.group(2));
                    String unit = m.group(3);
                    if (unit.equalsIgnoreCase("in"))
                        val *= 72.0f;   // convert to points
                    val += indentAdjustment * Math.signum(val);
                    String s = Float.toString(val);
                    int idx = s.indexOf('.');
                    if (idx != -1)
                        s = s.substring(0, idx + 2);
                    m.appendReplacement(sb, prop + ":" + s + "pt");
                }
                m.appendTail(sb);
                line = sb.toString();
            }

            // Insert spaces in list items
            if (listSpaces > 0) {
                m = listSpacesPattern.matcher(line);
                if (m.find()) {
                    sb.setLength(0);
                    sb.append(line.substring(0,m.end()));
                    for (int j = 0; j < listSpaces; j++)
                        sb.append("&nbsp;");
                    sb.append(line.substring(m.end()));
                    line = sb.toString();
                }
            }

            // Add some custom CSS
            if (line.contains("</head>")) {
                line = line.replace("</head>",
                    "<style>\n" +
                    "body {\n" +
                    "  width: " + bodyWidth + "px;\n" +
                    "  margin: auto;\n" +
                    "}\n" +
                    "</style>\n" +
                    "</head>\n");
                continue;
            }

            // Remove TOC formatting
            if (removeTOCLeaders) {
                m = TOCDotPattern.matcher(line);
                if (m.find()) {
                    line = m.replaceFirst("");
                    continue;
                }
            }

            // Remove page # references
            if (removePageRefs) {
                m = pageReferencePattern.matcher(line);
                if (m.find())
                    line = m.replaceAll("");
            }
        }
        Files.write(outputPath, lines, Charset.defaultCharset());
        return outputPath.getFileName().toString() + " written successfully.";
    }

    static void println(String msg) {
        System.out.println(msg);
    }

    static String slurpReaderText(Reader r, int bufferSize) {
        String s = "";
        try (BufferedReader br = new BufferedReader(r)) {
            char [] buffer = new char[bufferSize];
            StringBuilder sb = new StringBuilder(5*bufferSize);
            int n;
            while ((n = br.read(buffer, 0, buffer.length)) != -1)
                sb.append(buffer, 0, n);
            s = sb.toString();
        } catch (IOException ex) {
            // It's fine to do nothing and let the default empty string value of
            // 's' be returned.
        }
        return s;
    }
}
