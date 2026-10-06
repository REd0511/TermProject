package se233.termproject.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    // --- Layers ---
    @FXML private BorderPane mainEditorPane;
    @FXML private VBox initialDropZoneVBox;

    // --- Center Preview ---
    @FXML private ImageView originalImageView;
    @FXML private ImageView vectorizedImageView;

    // --- Top Panel (Zoom Controls) ---
    @FXML private Button zoomInBtn;
    @FXML private Button zoomOutBtn;
    @FXML private Button zoomFitBtn;

    // --- Right Panel (Configuration Settings) ---
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

    // --- Navigation & Export ---
    @FXML private Button backBtn;
    @FXML private Button nextBtn;
    @FXML private ProgressBar exportProgressBar;
    @FXML private List<File> uploadedFiles = new ArrayList<>();
    @FXML private int currentFileIndex = 0;

    @FXML
    public void initialize() {
        // Setup Color ChoiceBox
        customColorBox.setItems(FXCollections.observableArrayList(2, 3, 4, 5));
        customColorBox.setValue(2);
        customColorBox.disableProperty().bind(customColorRadio.selectedProperty().not());

        // 1. Tell the initial drop zone to accept file drops
        initialDropZoneVBox.setOnDragOver(event -> {
            if (event.getGestureSource() != initialDropZoneVBox && event.getDragboard().hasFiles()) {
                // Allow copy transfer mode
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        // 2. Handle the files when they are dropped
        initialDropZoneVBox.setOnDragDropped(event -> {
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
                        acceptedFiles.addAll(ArchiveExtractor.extractImages(file));
                    }
                }

                // Pass the valid files to your processing method
                if (!acceptedFiles.isEmpty()) {
                    handleNewFiles(acceptedFiles);
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void handleNewFiles(List<File> files) {
        // 1. Save the dropped files to your class variables
        this.uploadedFiles = files;
        this.currentFileIndex = 0; // Always start at the first image

        // 2. Load the image into the UI
        displayCurrentImage();

        // 3. Trigger the UI switch to the main editor
        showMainEditor();
    }
    private void displayCurrentImage() {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            File currentFile = uploadedFiles.get(currentFileIndex);

            // 1. Show original on the left
            javafx.scene.image.Image image = new javafx.scene.image.Image(currentFile.toURI().toString());
            originalImageView.setImage(image);

            // 2. Trigger the vectorization preview for the right side
            runVectorizationPreview(currentFile);
        }
    }

    private void runVectorizationPreview(File sourceFile) {
        System.out.println("Starting vectorization for: " + sourceFile.getName());

        boolean isHighDetail = detailHighBtn.isSelected();
        int colorCount = customColorRadio.isSelected() ? customColorBox.getValue() : 0;

        // Run vectorization engine
        File vectorFile = VectorizationTask.vectorize(sourceFile, isHighDetail, colorCount);

        if (vectorFile != null && vectorFile.exists()) {
            // Display the converted output in the right preview pane
            javafx.scene.image.Image vectorPreview = new javafx.scene.image.Image(vectorFile.toURI().toString());
            vectorizedImageView.setImage(vectorPreview);
        }
    }
    public void showMainEditor() {
        // 1. Swap the visible layers
        initialDropZoneVBox.setVisible(false);
        mainEditorPane.setVisible(true);

        // 2. Grab the current window (Stage) and expand it
        Stage stage = (Stage) mainEditorPane.getScene().getWindow();
        stage.setWidth(1200);
        stage.setHeight(800);

        // 3. Re-center the newly enlarged window on the user's screen
        stage.centerOnScreen();
    }
}