package se233.termproject.controller;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VectorizationTask {

    /**
     * Renders a raster preview PNG for JavaFX UI display.
     */
    public static File vectorize(File inputFile, String detailLevel, int colorCount, boolean removeBackground) throws Exception {
        File potraceBinary = resolvePotraceBinary();
        if (potraceBinary == null) {
            throw new FileNotFoundException("Potrace binary missing. Ensure executable exists in project resources.");
        }

        BufferedImage rawImage = ImageIO.read(inputFile);
        if (rawImage == null) {
            throw new IOException("Unable to read image file: " + inputFile.getName());
        }

        BufferedImage bufferedImage = flattenToWhite(rawImage);
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        String[] potraceArgs = getPotraceArgs(detailLevel);
        File outputPreviewPng = File.createTempFile("vector_preview_", ".png");

        int targetK = (colorCount > 0) ? colorCount : 12;
        List<Color> palette = extractKMeansPalette(bufferedImage, targetK);
        BufferedImage processedImage = quantizeToPalette(bufferedImage, palette);

        Map<Integer, Integer> counts = getPixelCounts(processedImage);
        palette.sort((c1, c2) -> counts.getOrDefault(c2.getRGB(), 0).compareTo(counts.getOrDefault(c1.getRGB(), 0)));

        BufferedImage finalComposite = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        if (!removeBackground) {
            Graphics2D g = finalComposite.createGraphics();
            g.setColor(palette.get(0));
            g.fillRect(0, 0, width, height);
            g.dispose();
        }

        for (int i = 1; i < palette.size(); i++) {
            Color color = palette.get(i);
            int colorRgb = color.getRGB();

            BufferedImage layerMask = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (processedImage.getRGB(x, y) == colorRgb) {
                        layerMask.setRGB(x, y, 0x000000);
                    } else {
                        layerMask.setRGB(x, y, 0xFFFFFF);
                    }
                }
            }

            File maskBmp = File.createTempFile("potrace_mask_", ".bmp");
            File layerPgm = File.createTempFile("potrace_layer_", ".pgm");
            ImageIO.write(layerMask, "bmp", maskBmp);

            List<String> cmd = new ArrayList<>();
            cmd.add(potraceBinary.getAbsolutePath());
            cmd.add("-b"); cmd.add("pgm");
            cmd.addAll(Arrays.asList(potraceArgs));
            cmd.add("-o"); cmd.add(layerPgm.getAbsolutePath());
            cmd.add(maskBmp.getAbsolutePath());

            Process process = new ProcessBuilder(cmd).start();
            process.waitFor();
            maskBmp.delete();

            if (layerPgm.exists()) {
                BufferedImage pgmLayer = readPGM(layerPgm);

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixelVal = pgmLayer.getRGB(x, y) & 0xFF;
                        if (pixelVal < 128) {
                            finalComposite.setRGB(x, y, (0xFF << 24) | (colorRgb & 0xFFFFFF));
                        }
                    }
                }
                layerPgm.delete();
            }
        }

        ImageIO.write(finalComposite, "png", outputPreviewPng);
        outputPreviewPng.deleteOnExit();
        return outputPreviewPng;
    }

    /**
     * Exports valid multi-layer SVG vector file using Potrace native SVG generation.
     */
    public static boolean exportToSvg(File inputFile, String detailLevel, int colorCount, boolean removeBackground, File outputSvgFile) throws Exception {
        File potraceBinary = resolvePotraceBinary();
        if (potraceBinary == null) {
            throw new FileNotFoundException("Potrace executable not found.");
        }

        BufferedImage rawImage = ImageIO.read(inputFile);
        if (rawImage == null) throw new IOException("Corrupted image file: " + inputFile.getName());

        BufferedImage bufferedImage = flattenToWhite(rawImage);
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        String[] potraceArgs = getPotraceArgs(detailLevel);

        int targetK = (colorCount > 0) ? colorCount : 12;
        List<Color> palette = extractKMeansPalette(bufferedImage, targetK);
        BufferedImage processedImage = quantizeToPalette(bufferedImage, palette);

        Map<Integer, Integer> counts = getPixelCounts(processedImage);
        palette.sort((c1, c2) -> counts.getOrDefault(c2.getRGB(), 0).compareTo(counts.getOrDefault(c1.getRGB(), 0)));

        StringBuilder svgBuilder = new StringBuilder();
        svgBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        svgBuilder.append(String.format("<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">\n",
                width, height, width, height));

        if (!removeBackground) {
            Color bgColor = palette.get(0);
            String hexColor = String.format("#%02x%02x%02x", bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue());
            svgBuilder.append(String.format("  <rect width=\"100%%\" height=\"100%%\" fill=\"%s\"/>\n", hexColor));
        }

        for (int i = 1; i < palette.size(); i++) {
            Color color = palette.get(i);
            int colorRgb = color.getRGB();
            String hexColor = String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());

            BufferedImage layerMask = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (processedImage.getRGB(x, y) == colorRgb) {
                        layerMask.setRGB(x, y, 0x000000);
                    } else {
                        layerMask.setRGB(x, y, 0xFFFFFF);
                    }
                }
            }

            File maskBmp = File.createTempFile("potrace_mask_", ".bmp");
            File layerSvg = File.createTempFile("potrace_layer_", ".svg");
            ImageIO.write(layerMask, "bmp", maskBmp);

            List<String> cmd = new ArrayList<>();
            cmd.add(potraceBinary.getAbsolutePath());
            cmd.add("-b"); cmd.add("svg");
            cmd.addAll(Arrays.asList(potraceArgs));
            cmd.add("-o"); cmd.add(layerSvg.getAbsolutePath());
            cmd.add(maskBmp.getAbsolutePath());

            Process process = new ProcessBuilder(cmd).start();
            process.waitFor();
            maskBmp.delete();

            if (layerSvg.exists()) {
                String layerContent = Files.readString(layerSvg.toPath());
                layerSvg.delete();

                // Preserve the <g transform="..."> wrapper tag generated by Potrace
                Pattern pattern = Pattern.compile("<g[^>]*>.*?</g>", Pattern.DOTALL);
                Matcher matcher = pattern.matcher(layerContent);
                if (matcher.find()) {
                    String gBlock = matcher.group(0);
                    // Inject palette layer fill color into group element
                    gBlock = gBlock.replaceAll("fill=\"#[0-9a-fA-F]+\"", "fill=\"" + hexColor + "\"");
                    gBlock = gBlock.replaceAll("fill=\"black\"", "fill=\"" + hexColor + "\"");
                    if (!gBlock.contains("fill=")) {
                        gBlock = gBlock.replace("<g ", "<g fill=\"" + hexColor + "\" ");
                    }
                    svgBuilder.append("  ").append(gBlock).append("\n");
                }
            }
        }

        svgBuilder.append("</svg>\n");

        Files.writeString(outputSvgFile.toPath(), svgBuilder.toString());
        return true;
    }

    private static String[] getPotraceArgs(String detailLevel) {
        String level = (detailLevel != null) ? detailLevel.toUpperCase() : "MEDIUM";
        return switch (level) {
            case "LOW" -> new String[]{"-t", "120", "-a", "0.0", "-O", "1.5"};
            case "HIGH" -> new String[]{"-t", "0", "-a", "0.8", "-O", "0.1"};
            default -> new String[]{"-t", "10", "-a", "1.0", "-O", "0.8"};
        };
    }

    private static BufferedImage flattenToWhite(BufferedImage source) {
        BufferedImage rgbImage = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rgbImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, source.getWidth(), source.getHeight());
        g2d.drawImage(source, 0, 0, null);
        g2d.dispose();
        return rgbImage;
    }

    private static List<Color> extractKMeansPalette(BufferedImage img, int maxColors) {
        int width = img.getWidth();
        int height = img.getHeight();
        List<Color> pixels = new ArrayList<>();
        int step = Math.max(1, Math.max(width, height) / 120);

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                pixels.add(new Color(img.getRGB(x, y)));
            }
        }

        Set<Integer> uniqueSet = new HashSet<>();
        for (Color c : pixels) uniqueSet.add(c.getRGB());

        if (uniqueSet.size() <= maxColors) {
            List<Color> exactPalette = new ArrayList<>();
            for (int rgb : uniqueSet) exactPalette.add(new Color(rgb));
            return exactPalette;
        }

        List<Color> centroids = new ArrayList<>();
        int stride = Math.max(1, pixels.size() / maxColors);
        for (int i = 0; i < maxColors && (i * stride) < pixels.size(); i++) {
            centroids.add(pixels.get(i * stride));
        }

        for (int iter = 0; iter < 6; iter++) {
            List<List<Color>> clusters = new ArrayList<>();
            for (int i = 0; i < centroids.size(); i++) clusters.add(new ArrayList<>());

            for (Color p : pixels) {
                int bestIdx = 0;
                double minDist = Double.MAX_VALUE;
                for (int c = 0; c < centroids.size(); c++) {
                    double dist = colorDistance(p, centroids.get(c));
                    if (dist < minDist) {
                        minDist = dist;
                        bestIdx = c;
                    }
                }
                clusters.get(bestIdx).add(p);
            }

            for (int c = 0; c < centroids.size(); c++) {
                List<Color> cluster = clusters.get(c);
                if (!cluster.isEmpty()) {
                    long r = 0, g = 0, b = 0;
                    for (Color p : cluster) {
                        r += p.getRed();
                        g += p.getGreen();
                        b += p.getBlue();
                    }
                    int sz = cluster.size();
                    centroids.set(c, new Color((int) (r / sz), (int) (g / sz), (int) (b / sz)));
                }
            }
        }
        return centroids;
    }

    private static BufferedImage quantizeToPalette(BufferedImage original, List<Color> palette) {
        int width = original.getWidth();
        int height = original.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color current = new Color(original.getRGB(x, y));
                Color closest = palette.get(0);
                double minDistance = Double.MAX_VALUE;

                for (Color paletteColor : palette) {
                    double dist = colorDistance(current, paletteColor);
                    if (dist < minDistance) {
                        minDistance = dist;
                        closest = paletteColor;
                    }
                }
                result.setRGB(x, y, closest.getRGB());
            }
        }
        return result;
    }

    private static Map<Integer, Integer> getPixelCounts(BufferedImage img) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                counts.put(rgb, counts.getOrDefault(rgb, 0) + 1);
            }
        }
        return counts;
    }

    private static double colorDistance(Color c1, Color c2) {
        int dr = c1.getRed() - c2.getRed();
        int dg = c1.getGreen() - c2.getGreen();
        int db = c1.getBlue() - c2.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static File resolvePotraceBinary() {
        String os = System.getProperty("os.name").toLowerCase();
        String extension = os.contains("win") ? ".exe" : "";

        File f1 = new File("src/main/resources/se233/termproject/tools/potrace" + extension);
        if (f1.exists()) {
            f1.setExecutable(true);
            return f1;
        }

        File f2 = new File("target/classes/se233/termproject/tools/potrace" + extension);
        if (f2.exists()) {
            f2.setExecutable(true);
            return f2;
        }

        return null;
    }

    private static BufferedImage readPGM(File pgmFile) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(pgmFile))) {
            String magic = readToken(in);
            if (!"P5".equalsIgnoreCase(magic)) {
                throw new IOException("Unsupported PGM format: " + magic);
            }
            int width = Integer.parseInt(readToken(in));
            int height = Integer.parseInt(readToken(in));
            Integer.parseInt(readToken(in));

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

            int offset = 0;
            while (offset < pixels.length) {
                int read = in.read(pixels, offset, pixels.length - offset);
                if (read == -1) break;
                offset += read;
            }
            return image;
        }
    }

    private static String readToken(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '#') {
                while ((c = in.read()) != -1 && c != '\n' && c != '\r');
            } else if (!Character.isWhitespace(c)) {
                sb.append((char) c);
                break;
            }
        }
        while ((c = in.read()) != -1) {
            if (Character.isWhitespace(c)) break;
            sb.append((char) c);
        }
        return sb.toString();
    }
}