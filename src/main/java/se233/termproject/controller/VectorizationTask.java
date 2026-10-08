package se233.termproject.controller;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class VectorizationTask {

    public static File vectorize(File inputFile, boolean highDetail, int colorCount) {
        try {
            // 1. Convert PNG/JPG -> BMP for Potrace input
            BufferedImage bufferedImage = ImageIO.read(inputFile);
            File tempBmp = File.createTempFile("potrace_input_", ".bmp");
            ImageIO.write(bufferedImage, "bmp", tempBmp);

            // 2. Output BMP/PGM preview file for JavaFX ImageView
            File outputPreview = File.createTempFile("vector_preview_", ".bmp");

            // Path to potrace binary
            String potracePath = "src/main/resources/se233/termproject/tools/potrace";

            // Build ProcessBuilder command
            // Note: -b bmp outputs a BMP file that JavaFX ImageView can render natively
            ProcessBuilder pb = new ProcessBuilder(
                    potracePath,
                    "-b", "bmp", // Output BMP format for JavaFX preview
                    "-o", outputPreview.getAbsolutePath(),
                    tempBmp.getAbsolutePath()
            );

            Process process = pb.start();
            int exitCode = process.waitFor();

            tempBmp.deleteOnExit();

            if (exitCode == 0) {
                return outputPreview;
            } else {
                System.err.println("Potrace process failed with exit code: " + exitCode);
            }
        } catch (Exception e) {
            System.err.println("Error executing vectorization: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}