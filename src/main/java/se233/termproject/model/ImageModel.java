package se233.termproject.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageModel {
    private List<File> uploadedFiles = new ArrayList<>();
    private int currentFileIndex = 0;

    public void setFiles(List<File> files) {
        this.uploadedFiles = files;
        this.currentFileIndex = 0;
    }

    public List<File> getUploadedFiles() {
        return uploadedFiles;
    }

    public File getCurrentFile() {
        if (uploadedFiles != null && !uploadedFiles.isEmpty() && currentFileIndex >= 0 && currentFileIndex < uploadedFiles.size()) {
            return uploadedFiles.get(currentFileIndex);
        }
        return null;
    }

    public boolean hasNext() {
        return uploadedFiles != null && currentFileIndex < uploadedFiles.size() - 1;
    }

    public boolean hasPrevious() {
        return uploadedFiles != null && currentFileIndex > 0;
    }

    public void next() {
        if (hasNext()) currentFileIndex++;
    }

    public void previous() {
        if (hasPrevious()) currentFileIndex--;
    }

    public boolean isEmpty() {
        return uploadedFiles == null || uploadedFiles.isEmpty();
    }

    public int getSize() {
        return uploadedFiles == null ? 0 : uploadedFiles.size();
    }
}