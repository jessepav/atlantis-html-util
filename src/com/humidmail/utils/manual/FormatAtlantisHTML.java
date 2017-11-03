package com.humidmail.utils.manual;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import java.nio.charset.Charset;

import com.sanityinc.jargs.CmdLineParser;
import com.sanityinc.jargs.CmdLineParser.Option;

public class FormatAtlantisHTML
{
    private static int bodyWidth;
    private static float[] fontSizeAdjustment, fontSizeThreshold;

    private static String usageText;

    public static void main(String[] args) throws IOException {
        CmdLineParser parser = new CmdLineParser();
        Option<Integer> width = parser.addIntegerOption('w', "width");
        Option<String> size = parser.addStringOption('f', "fontsize");
        Option<String> threshold = parser.addStringOption('t', "threshold");
        Option<Boolean> help = parser.addBooleanOption('h', "help");
        try {
            parser.parse(args);
        } catch (CmdLineParser.OptionException e) {
            println(e.getMessage());
            println(getUsageText());
            System.exit(2);
        }
        String[] files = parser.getRemainingArgs();
        if (parser.getOptionValue(help, Boolean.FALSE) || files.length == 0) {
            println(getUsageText());
            System.exit(1);
        }
        bodyWidth = parser.getOptionValue(width, Integer.valueOf(750));
        fontSizeAdjustment = new float[] {4.0f,3.0f,21.0f};
        fontSizeThreshold = new float[] {10f, 17f, 20f};
        setFloatArray(fontSizeAdjustment, parser.getOptionValue(size));
        setFloatArray(fontSizeThreshold, parser.getOptionValue(threshold));
        println("Body Width: " + bodyWidth + "px");
        println("Font Size Adjustment: " + Arrays.toString(fontSizeAdjustment));
        println("Font Size Threshold: " + Arrays.toString(fontSizeThreshold));
        for (String file : files) {
            println("Processing " + file);
            processFile(file);
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

    private static String getUsageText() throws UnsupportedEncodingException {
        if (usageText == null) {
            InputStream in = FormatAtlantisHTML.class.getResourceAsStream("/resources/usage.txt");
            Reader reader = new InputStreamReader(in, "UTF-8");
            usageText = slurpReaderText(reader, 1024);
        }
        return usageText;
    }

    private static final Pattern fontSizePattern = Pattern.compile("font-size\\s{0,2}:{0,2}(\\d[\\d\\.]*)pt");
    private static final Pattern TOCDotPattern = Pattern.compile("\\.{15,}\\d+");
    private static final Pattern pageReferencePattern = Pattern.compile("\\s?\\(see p\\.\\d+\\)");

    private static void processFile(String file) throws IOException {
        Path path = FileSystems.getDefault().getPath(file);
        if (!Files.exists(path))
            return;
        List<String> lines = Files.readAllLines(path, Charset.defaultCharset());
        String line;
        for (int i = 0; i < lines.size(); lines.set(i++, line)) {
            line = lines.get(i);

            // Check if this file has already been processed; if so, skip it
            if (line.contains("content=\"FormatAtlantisHTML\"")) {
                println("   " + file + " has already been processed; skipping.");
                return;
            }

            // Add a meta tag indicating the file has been processed
            if (line.contains("<meta name=Generator") || line.contains("<meta name=\"Generator\"")) {
                line = line +
                    "\n<meta name=\"Processor\" content=\"FormatAtlantisHTML\">";
                continue;
            }

            // Fix up the font size
            Matcher m = fontSizePattern.matcher(line);
            if (m.find()) {
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
                line = m.replaceAll("font-size:" + s + "pt");
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
            m = TOCDotPattern.matcher(line);
            if (m.find()) {
                line = m.replaceFirst("");
                continue;
            }

            // Remove page # references
            m = pageReferencePattern.matcher(line);
            if (m.find())
                line = m.replaceAll("");
        }
        Files.write(path, lines, Charset.defaultCharset());
    }

    static void println(String msg) {
        System.out.println(msg);
    }

    public static String slurpReaderText(Reader r, int bufferSize) {
        String s = null;
        BufferedReader reader = null;
        try {
            try {
                reader = new BufferedReader(r);
                char [] buffer = new char[bufferSize];
                StringBuilder sb = new StringBuilder(5*bufferSize);
                int n;
                while ((n = reader.read(buffer, 0, buffer.length)) != -1)
                    sb.append(buffer, 0, n);
                s = sb.toString();
            } finally {
                if (reader != null)
                    reader.close();
            }
        } catch (IOException ex) {
            s = null;
        }
        return s;
    }
}
