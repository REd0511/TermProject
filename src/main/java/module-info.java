module se233.termproject {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop; // Gives access to ImageIO and BufferedImage

    opens se233.termproject to javafx.fxml;
    exports se233.termproject;

    opens se233.termproject.controller to javafx.fxml;
    exports se233.termproject.controller;
}