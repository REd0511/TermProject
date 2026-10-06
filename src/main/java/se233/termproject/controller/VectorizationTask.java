package se233.termproject.controller;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class VectorizationTask {

    public static File vectorize(File inputFile, boolean highDetail, int colorCount) {
        try {
            // 1. Potrace requires .bmp input format, so convert PNG/JPG -> BMP first
            BufferedImage bufferedImage = ImageIO.read(inputFile);
            File tempBmp = File.createTempFile("potrace_input_", ".bmp");
            ImageIO.write(bufferedImage, "bmp", tempBmp);

            // 2. Output SVG file destination
            File outputSvg = File.createTempFile("vector_output_", ".svg");

            // 3. Locate potrace binary in your tools directory (or system PATH)
            // Adjust executable name ("potrace" or "potrace.exe" depending on OS)
            String potracePath = "src/main/resources/se233/termproject/tools/potrace";

            // Build Potrace CLI Arguments
            ProcessBuilder pb = new ProcessBuilder(
                    potracePath,
                    "-s", // Output SVG format
                    "-o", outputSvg.getAbsolutePath(),
                    tempBmp.getAbsolutePath()
            );

            Process process = pb.start();
            int exitCode = process.waitFor();

            // Cleanup temp BMP
            tempBmp.deleteOnExit();

            if (exitCode == 0) {
                return outputSvg;
            } else {
                System.err.println("Potrace process failed with exit code: " + exitCode);
            }
        } catch (Exception e) {
            System.err.println("Error executing vectorization: " + e.getMessage());
        }
        return null;
    }
}