package com.uniqueapps.musemixe;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class MusicApplication extends Application {

    private HomeController homeController;

    @Override
    public void start(Stage stage) throws IOException {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(MusicApplication.class.getResource("home.fxml")));
        Scene scene = new Scene(loader.load(), 800, 600);
        scene.getStylesheets().add(Objects.requireNonNull(MusicApplication.class.getResource("style.css")).toExternalForm());
        homeController = loader.getController();
        stage.setTitle("MIDI Player");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (homeController != null) {
            homeController.shutdownScheduler();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}