package se233.termproject.model;

import java.util.List;

public class ExportSettings {
    private String detailLevel = "MEDIUM";
    private int colorCount = 2;
    private boolean removeBackground = false;
    private List<String> customHexColors = null;

    public String getDetailLevel() {
        return detailLevel;
    }

    public void setDetailLevel(String detailLevel) {
        this.detailLevel = detailLevel;
    }

    public int getColorCount() {
        return colorCount;
    }

    public void setColorCount(int colorCount) {
        this.colorCount = colorCount;
    }

    public boolean isRemoveBackground() {
        return removeBackground;
    }

    public void setRemoveBackground(boolean removeBackground) {
        this.removeBackground = removeBackground;
    }

    public List<String> getCustomHexColors() {
        return customHexColors;
    }

    public void setCustomHexColors(List<String> customHexColors) {
        this.customHexColors = customHexColors;
    }

    public void resetCustomColors() {
        this.customHexColors = null;
    }
}