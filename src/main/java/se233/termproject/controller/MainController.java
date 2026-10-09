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

    @FXML private Button backBtn;
    @FXML private Button nextBtn;
    @FXML private ProgressBar exportProgressBar;

    @FXML private List<File> uploadedFiles = new ArrayList<>();
    @FXML private int currentFileIndex = 0;

    private double currentScale = 1.0;
    private static final double ZOOM_STEP = 0.25;
    private static final double MIN_SCALE = 0.25;
    private static final double MAX_SCALE = 5.0;

    private String selectedDetailLevel = "MEDIUM";

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
        if (uploadedFiles != null && currentFileIndex < uploadedFiles.size() - 1) {
            currentFileIndex++;
            displayCurrentImage();
        }
    }

    @FXML
    private void handleBack() {
        if (uploadedFiles != null && currentFileIndex > 0) {
            currentFileIndex--;
            displayCurrentImage();
        }
    }

    private void handleNewFiles(List<File> files) {
        this.uploadedFiles = files;
        this.currentFileIndex = 0;
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
                    if (img != null) {
                        countUniqueColors(img);
                    }
                } catch (Exception ignored) {}

                int done = completed.incrementAndGet();
                Platform.runLater(() -> {
                    if (exportProgressBar != null) {
                        exportProgressBar.setProgress((double) done / total);
                        if (done == total) {
                            exportProgressBar.setProgress(0.0);
                        }
                    }
                });
            });
        }
        executor.shutdown();
    }

    @FXML
    private void handleExportAll() {
        if (uploadedFiles == null || uploadedFiles.isEmpty()) {
            showWarning("No Images Loaded", "Please upload image files before exporting.");
            return;
        }

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Destination Folder for SVG Export");
        File selectedDir = directoryChooser.showDialog(mainEditorPane.getScene().getWindow());

        if (selectedDir == null) return;

        int colorCount = 0;
        if (customColorRadio != null && customColorRadio.isSelected() && customColorBox != null && customColorBox.getValue() != null) {
            colorCount = customColorBox.getValue();
        }
        boolean removeBg = (removeBackgroundCheck != null && removeBackgroundCheck.isSelected());
        int finalColorCount = colorCount;

        setControlsDisabled(true);

        int numThreads = Math.min(Runtime.getRuntime().availableProcessors(), uploadedFiles.size());
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger completedTasks = new AtomicInteger(0);
        int totalFiles = uploadedFiles.size();

        if (exportProgressBar != null) exportProgressBar.setProgress(0.0);

        for (File src : uploadedFiles) {
            executor.submit(() -> {
                try {
                    String baseName = src.getName().replaceAll("(?i)\\.(png|jpg|jpeg)$", "");
                    File targetSvg = new File(selectedDir, baseName + ".svg");

                    int counter = 1;
                    while (targetSvg.exists()) {
                        targetSvg = new File(selectedDir, baseName + "_" + counter + ".svg");
                        counter++;
                    }

                    VectorizationTask.exportToSvg(src, selectedDetailLevel, finalColorCount, removeBg, targetSvg);

                } catch (Exception e) {
                    Platform.runLater(() -> showError("Export Failed", "Error exporting " + src.getName()));
                } finally {
                    int done = completedTasks.incrementAndGet();
                    Platform.runLater(() -> {
                        if (exportProgressBar != null) {
                            exportProgressBar.setProgress((double) done / totalFiles);
                        }
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
        if (detailLowBtn != null && detailLowBtn.isSelected()) {
            selectedDetailLevel = "LOW";
        } else if (detailHighBtn != null && detailHighBtn.isSelected()) {
            selectedDetailLevel = "HIGH";
        } else {
            selectedDetailLevel = "MEDIUM";
        }
        triggerVectorization();
    }

    private void updateNavigationButtons() {
        if (backBtn != null) backBtn.setDisable(currentFileIndex <= 0);
        if (nextBtn != null) nextBtn.setDisable(uploadedFiles == null || currentFileIndex >= uploadedFiles.size() - 1);
    }

    private void triggerVectorization() {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            File currentFile = uploadedFiles.get(currentFileIndex);
            runVectorizationPreview(currentFile);
        }
    }

    private void runVectorizationPreview(File sourceFile) {
        int colorCount = 0;
        if (customColorRadio != null && customColorRadio.isSelected() && customColorBox != null && customColorBox.getValue() != null) {
            colorCount = customColorBox.getValue();
        }

        boolean removeBg = (removeBackgroundCheck != null && removeBackgroundCheck.isSelected());
        int finalColorCount = colorCount;

        Task<File> previewTask = new Task<>() {
            @Override
            protected File call() throws Exception {
                return VectorizationTask.vectorize(sourceFile, selectedDetailLevel, finalColorCount, removeBg);
            }
        };

        previewTask.setOnSucceeded(e -> {
            File vectorFile = previewTask.getValue();
            if (vectorFile != null && vectorFile.exists() && vectorizedImageView != null) {
                vectorizedImageView.setImage(null);
                Image vectorPreview = new Image(vectorFile.toURI().toString(), false);
                vectorizedImageView.setImage(vectorPreview);
            }
        });

        previewTask.setOnFailed(e -> {
            Throwable ex = previewTask.getException();
            showError("Vectorization Error", ex != null ? ex.getMessage() : "Failed to vectorize preview.");
        });

        new Thread(previewTask).start();
    }

    private void displayCurrentImage() {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            handleZoomToFit();
            File currentFile = uploadedFiles.get(currentFileIndex);

            try {
                Image image = new Image(currentFile.toURI().toString());
                if (originalImageView != null) originalImageView.setImage(image);

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
            for (int i = 2; i <= maxColors; i++) {
                colorOptions.add(i);
            }

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
        if (backBtn != null) backBtn.setDisable(disabled || currentFileIndex <= 0);
        if (nextBtn != null) nextBtn.setDisable(disabled || uploadedFiles == null || currentFileIndex >= uploadedFiles.size() - 1);
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
}