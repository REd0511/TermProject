module se233.termproject {
    requires javafx.controls;
    requires javafx.fxml;

    opens se233.termproject to javafx.fxml;
    exports se233.termproject;

    // Add these two lines to give JavaFX access to your MainController
    opens se233.termproject.controller to javafx.fxml;
    exports se233.termproject.controller;
}