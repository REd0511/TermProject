package se233.termproject.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import se233.termproject.model.ExportSettings;
import se233.termproject.model.ImageModel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainController {

    @FXML private BorderPane mainEditorPane;
    @FXML private VBox initialDropZoneVBox;
    @FXML private ImageView originalImageView;
    @FXML private ImageView vectorizedImageView;
    @FXML private Label originalNameLabel;
    @FXML private Label vectorStatusLabel;
    @FXML private Button zoomInBtn;
    @FXML private Button zoomOutBtn;
    @FXML private Button zoomFitBtn;
    @FXML private Button exportAllBtn;
    @FXML private ToggleGroup detailGroup;
    @FXML private ToggleButton detailLowBtn;
    @FXML private ToggleButton detailMedBtn;
    @FXML private ToggleButton detailHighBtn;
    @FXML private ToggleGroup colorGroup;
    @FXML private RadioButton unlimitedColorRadio;
    @FXML private RadioButton customColorRadio;
    @FXML private ChoiceBox<Integer> customColorBox;
    @FXML private CheckBox removeBackgroundCheck;
    @FXML private VBox colorPaletteContainer;
    @FXML private javafx.scene.layout.FlowPane colorPaletteFlow;
    @FXML private Button backBtn;
    @FXML private Button nextBtn;
    @FXML private ProgressBar exportProgressBar;

    // --- เชื่อมต่อกับ Model ---
    private ImageModel imageModel = new ImageModel();
    private ExportSettings settings = new ExportSettings();

    private boolean isUpdatingPalette = false;
    private double currentScale = 1.0;
    private static final double ZOOM_STEP = 0.25;
    private static final double MIN_SCALE = 0.25;
    private static final double MAX_SCALE = 5.0;

    @FXML
    public void initialize() {
        if (customColorBox != null && customColorRadio != null) {
            customColorBox.disableProperty().bind(customColorRadio.selectedProperty().not());
        }
        if (detailGroup != null) {
            detailGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null) handleDetailChange();
            });
        }
        if (colorGroup != null) {
            colorGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> triggerVectorization());
        }
        if (customColorBox != null) {
            customColorBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && customColorRadio != null && customColorRadio.isSelected()) {
                    triggerVectorization();
                }
            });
        }
        if (removeBackgroundCheck != null) {
            removeBackgroundCheck.selectedProperty().addListener((obs, oldVal, newVal) -> triggerVectorization());
        }
        if (customColorRadio != null) {
            customColorRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (colorPaletteContainer != null) {
                    colorPaletteContainer.setVisible(newVal);
                    colorPaletteContainer.setManaged(newVal);
                }
            });
        }
        setupDragAndDrop(initialDropZoneVBox);
        setupDragAndDrop(mainEditorPane);
    }

    private void setupDragAndDrop(javafx.scene.Node node) {
        if (node == null) return;
        node.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        node.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                List<File> acceptedFiles = new ArrayList<>();
                for (File file : db.getFiles()) {
                    String fileName = file.getName().toLowerCase();
                    if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                        acceptedFiles.add(file);
                    } else if (fileName.endsWith(".zip")) {
                        try {
                            acceptedFiles.addAll(ArchiveExtractor.extractImages(file));
                        } catch (Exception ex) {
                            showError("ZIP Extraction Error", "Failed to extract archive: " + file.getName());
                        }
                    }
                }
                if (!acceptedFiles.isEmpty()) {
                    handleNewFiles(acceptedFiles);
                } else {
                    showWarning("Unsupported Format", "Please drag JPG, PNG, or ZIP files.");
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    @FXML
    private void handleNext() {
        if (imageModel.hasNext()) {
            imageModel.next();
            displayCurrentImage();
        }
    }

    @FXML
    private void handleBack() {
        if (imageModel.hasPrevious()) {
            imageModel.previous();
            displayCurrentImage();
        }
    }

    private void handleNewFiles(List<File> files) {
        imageModel.setFiles(files);
        showMainEditor();
        displayCurrentImage();

        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), files.size());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger completed = new AtomicInteger(0);
        int total = files.size();

        if (exportProgressBar != null) exportProgressBar.setProgress(0.0);

        for (File file : files) {
            executor.submit(() -> {
                try {
                    BufferedImage img = ImageIO.read(file);
                    if (img != null) countUniqueColors(img);
                } catch (Exception ignored) {}

                int done = completed.incrementAndGet();
                Platform.runLater(() -> {
                    if (exportProgressBar != null) {
                        exportProgressBar.setProgress((double) done / total);
                        if (done == total) exportProgressBar.setProgress(0.0);
                    }
                });
            });
        }
        executor.shutdown();
    }

    @FXML
    private void handleExportAll() {
        if (imageModel.isEmpty()) {
            showWarning("No Images Loaded", "Please upload image files before exporting.");
            return;
        }

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Destination Folder for SVG Export");
        File selectedDir = directoryChooser.showDialog(mainEditorPane.getScene().getWindow());

        if (selectedDir == null) return;

        syncSettingsFromUI();
        setControlsDisabled(true);

        int numThreads = Math.min(Runtime.getRuntime().availableProcessors(), imageModel.getSize());
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger completedTasks = new AtomicInteger(0);
        int totalFiles = imageModel.getSize();

        if (exportProgressBar != null) exportProgressBar.setProgress(0.0);

        for (File src : imageModel.getUploadedFiles()) {
            executor.submit(() -> {
                try {
                    String baseName = src.getName().replaceAll("(?i)\\.(png|jpg|jpeg)$", "");
                    File targetSvg = new File(selectedDir, baseName + ".svg");

                    int counter = 1;
                    while (targetSvg.exists()) {
                        targetSvg = new File(selectedDir, baseName + "_" + counter + ".svg");
                        counter++;
                    }

                    List<String> exportUserColors = settings.getColorCount() > 0 && settings.getCustomHexColors() != null
                            ? new ArrayList<>(settings.getCustomHexColors()) : null;

                    VectorizationTask.exportToSvg(src, settings.getDetailLevel(), settings.getColorCount(), settings.isRemoveBackground(), exportUserColors, targetSvg);

                } catch (Exception e) {
                    Platform.runLater(() -> showError("Export Failed", "Error exporting " + src.getName()));
                } finally {
                    int done = completedTasks.incrementAndGet();
                    Platform.runLater(() -> {
                        if (exportProgressBar != null) exportProgressBar.setProgress((double) done / totalFiles);
                        if (done == totalFiles) {
                            setControlsDisabled(false);
                            showInfo("Export Complete", "Successfully exported " + totalFiles + " SVG vector file(s) to:\n" + selectedDir.getAbsolutePath());
                        }
                    });
                }
            });
        }
        executor.shutdown();
    }

    @FXML
    private void handleDetailChange() {
        if (detailLowBtn != null && detailLowBtn.isSelected()) settings.setDetailLevel("LOW");
        else if (detailHighBtn != null && detailHighBtn.isSelected()) settings.setDetailLevel("HIGH");
        else settings.setDetailLevel("MEDIUM");

        triggerVectorization();
    }

    private void updateNavigationButtons() {
        if (backBtn != null) backBtn.setDisable(!imageModel.hasPrevious());
        if (nextBtn != null) nextBtn.setDisable(!imageModel.hasNext());
    }

    private void syncSettingsFromUI() {
        if (customColorRadio != null && customColorRadio.isSelected() && customColorBox != null && customColorBox.getValue() != null) {
            settings.setColorCount(customColorBox.getValue());
        } else {
            settings.setColorCount(0);
        }
        settings.setRemoveBackground(removeBackgroundCheck != null && removeBackgroundCheck.isSelected());
    }

    private void triggerVectorization() {
        if (!imageModel.isEmpty()) {
            runVectorizationPreview(imageModel.getCurrentFile());
        }
    }

    private void runVectorizationPreview(File sourceFile) {
        syncSettingsFromUI();
        List<String> userColors = settings.getColorCount() > 0 && settings.getCustomHexColors() != null
                ? new ArrayList<>(settings.getCustomHexColors()) : null;

        if (vectorStatusLabel != null) vectorStatusLabel.setText("Processing...");

        Task<Object[]> previewTask = new Task<Object[]>() {
            @Override
            protected Object[] call() throws Exception {
                return VectorizationTask.vectorizeAndGetPalette(sourceFile, settings.getDetailLevel(), settings.getColorCount(), settings.isRemoveBackground(), userColors);
            }
        };

        previewTask.setOnSucceeded(e -> {
            Object[] result = previewTask.getValue();
            File vectorFile = (File) result[0];

            @SuppressWarnings("unchecked")
            List<java.awt.Color> palette = (List<java.awt.Color>) result[1];

            if (vectorFile != null && vectorFile.exists() && vectorizedImageView != null) {
                vectorizedImageView.setImage(null);
                Image vectorPreview = new Image(vectorFile.toURI().toString(), false);
                vectorizedImageView.setImage(vectorPreview);
            }
            if (vectorStatusLabel != null) vectorStatusLabel.setText("Vectorization Completed");
            updateColorPaletteUI(palette);
        });

        previewTask.setOnFailed(e -> {
            Throwable ex = previewTask.getException();
            showError("Vectorization Error", ex != null ? ex.getMessage() : "Failed to vectorize preview.");
        });

        new Thread(previewTask).start();
    }

    private void displayCurrentImage() {
        if (!imageModel.isEmpty()) {
            handleZoomToFit();
            File currentFile = imageModel.getCurrentFile();
            try {
                Image image = new Image(currentFile.toURI().toString());
                if (originalImageView != null) originalImageView.setImage(image);
                if (originalNameLabel != null) originalNameLabel.setText("File: " + currentFile.getName());
                updateColorLimitsForImage(currentFile);
                runVectorizationPreview(currentFile);
                updateNavigationButtons();
            } catch (Exception e) {
                showError("File Load Error", "Could not load image: " + currentFile.getName());
            }
        }
    }

    private void updateColorLimitsForImage(File imageFile) {
        try {
            BufferedImage bufferedImage = ImageIO.read(imageFile);
            if (bufferedImage == null || customColorBox == null) return;
            int detectedColorCount = countUniqueColors(bufferedImage);
            int maxColors = Math.min(Math.max(detectedColorCount, 2), 5);
            List<Integer> colorOptions = new ArrayList<>();
            for (int i = 2; i <= maxColors; i++) colorOptions.add(i);

            customColorBox.setItems(FXCollections.observableArrayList(colorOptions));
            customColorBox.setValue(2);
            if (customColorRadio != null) customColorRadio.setSelected(true);
        } catch (IOException e) {
            showError("Image Read Error", "Could not read color data for: " + imageFile.getName());
        }
    }

    private int countUniqueColors(BufferedImage image) {
        Set<Integer> uniqueColors = new HashSet<>();
        int width = image.getWidth();
        int height = image.getHeight();
        int stepX = Math.max(1, width / 150);
        int stepY = Math.max(1, height / 150);
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                uniqueColors.add(rgb);
                if (uniqueColors.size() > 5) return 6;
            }
        }
        return uniqueColors.size();
    }

    private void setControlsDisabled(boolean disabled) {
        if (exportAllBtn != null) exportAllBtn.setDisable(disabled);
        if (backBtn != null) backBtn.setDisable(disabled || !imageModel.hasPrevious());
        if (nextBtn != null) nextBtn.setDisable(disabled || !imageModel.hasNext());
    }

    @FXML private void handleZoomIn() { if (currentScale < MAX_SCALE) { currentScale += ZOOM_STEP; applyZoom(); } }
    @FXML private void handleZoomOut() { if (currentScale > MIN_SCALE) { currentScale -= ZOOM_STEP; applyZoom(); } }
    @FXML private void handleZoomToFit() { currentScale = 1.0; applyZoom(); }

    private void applyZoom() {
        if (originalImageView != null) {
            originalImageView.setScaleX(currentScale);
            originalImageView.setScaleY(currentScale);
        }
        if (vectorizedImageView != null) {
            vectorizedImageView.setScaleX(currentScale);
            vectorizedImageView.setScaleY(currentScale);
        }
    }

    public void showMainEditor() {
        if (initialDropZoneVBox != null && mainEditorPane != null) {
            initialDropZoneVBox.setVisible(false);
            mainEditorPane.setVisible(true);
            Stage stage = (Stage) mainEditorPane.getScene().getWindow();
            if (stage != null) {
                stage.setWidth(1200);
                stage.setHeight(800);
                stage.centerOnScreen();
            }
        }
    }

    private void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void showWarning(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void showInfo(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void updateColorPaletteUI(List<java.awt.Color> extractedPalette) {
        if (isUpdatingPalette || colorPaletteFlow == null) return;
        isUpdatingPalette = true;
        colorPaletteFlow.getChildren().clear();

        if (settings.getCustomHexColors() == null || settings.getCustomHexColors().size() != extractedPalette.size()) {
            List<String> newHexList = new ArrayList<>();
            for (java.awt.Color c : extractedPalette) {
                newHexList.add(String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()));
            }
            settings.setCustomHexColors(newHexList);
        }

        for (int i = 0; i < settings.getCustomHexColors().size(); i++) {
            final int index = i;
            String hex = settings.getCustomHexColors().get(i);
            ColorPicker picker = new ColorPicker(javafx.scene.paint.Color.web(hex));
            picker.setStyle("-fx-color-label-visible: false; -fx-pref-width: 40px;");

            picker.setOnAction(event -> {
                javafx.scene.paint.Color fxColor = picker.getValue();
                String newHex = String.format("#%02x%02x%02x",
                        (int)(fxColor.getRed()*255), (int)(fxColor.getGreen()*255), (int)(fxColor.getBlue()*255));
                settings.getCustomHexColors().set(index, newHex);
                triggerVectorization();
            });
            colorPaletteFlow.getChildren().add(picker);
        }

        if (customColorRadio != null && customColorRadio.isSelected()) {
            colorPaletteContainer.setVisible(true);
            colorPaletteContainer.setManaged(true);
        }
        isUpdatingPalette = false;
    }

    @FXML
    private void handleResetColors() {
        settings.resetCustomColors();
        triggerVectorization();
    }
}