package se233.termproject.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ArchiveExtractor {

    public static List<File> extractImages(File zipArchive) {
        List<File> extractedImages = new ArrayList<>();

        try {
            // Create a hidden temporary folder on the OS to hold the unzipped files
            Path tempDir = Files.createTempDirectory("vector_app_extracted_");
            ZipFile zipFile = new ZipFile(zipArchive);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().toLowerCase();

                // Only extract standard image formats, ignore other files or folders in the zip
                if (!entry.isDirectory() && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {

                    // Prevent issues if files are inside folders within the zip
                    File tempFile = new File(tempDir.toFile(), new File(entry.getName()).getName());

                    try (InputStream is = zipFile.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(tempFile)) {

                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = is.read(buffer)) >= 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                    extractedImages.add(tempFile);
                }
            }
            zipFile.close();
        } catch (Exception e) {
            System.err.println("Error extracting ZIP file: " + e.getMessage());
        }

        return extractedImages;
    }
}