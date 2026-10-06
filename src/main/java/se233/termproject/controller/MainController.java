package se233.termproject.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

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

    @FXML
    public void initialize() {
        // Populate the custom color box with values 2 to 5
        customColorBox.setItems(FXCollections.observableArrayList(2, 3, 4, 5));
        customColorBox.setValue(2); // Set default to 2 colors[cite: 484]

        // Disable the ChoiceBox unless "Custom" is selected
        customColorBox.disableProperty().bind(customColorRadio.selectedProperty().not());
    }

    // Mary will call this method when a file is dropped
    public void showMainEditor() {
        // 1. Swap the visible layers
        initialDropZoneVBox.setVisible(false);
        mainEditorPane.setVisible(true);

        // 2. Grab the current window (Stage) and expand it
        javafx.stage.Stage stage = (javafx.stage.Stage) mainEditorPane.getScene().getWindow();
        stage.setWidth(1200);
        stage.setHeight(800);

        // 3. Re-center the newly enlarged window on the user's screen
        stage.centerOnScreen();
    }
}