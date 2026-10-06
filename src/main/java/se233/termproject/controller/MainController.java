package se233.termproject.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;

public class MainController {

    // --- Center Preview & Drop Zone ---
    @FXML private Pane dropZonePane;
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

    @FXML
    public void initialize() {
        // Populate the custom color box with values 2 to 5[cite: 244]
        customColorBox.setItems(FXCollections.observableArrayList(2, 3, 4, 5));
        customColorBox.setValue(2); // Set default to 2 colors[cite: 244]

        // Disable the ChoiceBox unless "Custom" is selected
        customColorBox.disableProperty().bind(customColorRadio.selectedProperty().not());

        // You and Mary can add your event listeners here later
        // Example: exportAllBtn.setOnAction(e -> handleBatchExport());
    }
}