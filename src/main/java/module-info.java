module se233.termproject {
    requires javafx.controls;
    requires javafx.fxml;


    opens se233.termproject to javafx.fxml;
    exports se233.termproject;
}