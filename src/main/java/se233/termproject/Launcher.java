package se233.termproject;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

// Launcher now extends Application directly
public class Launcher extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Loads your main-view.fxml directly
        FXMLLoader fxmlLoader = new FXMLLoader(Launcher.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);
        stage.setTitle("Vector Graphics Conversion Utility");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}