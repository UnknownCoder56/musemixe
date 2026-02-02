package com.uniqueapps.musemix;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class MusemixApplication extends Application {

    private HomeController homeController;

    @Override
    public void start(Stage stage) throws IOException {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(MusemixApplication.class.getResource("home.fxml")));
        Scene scene = new Scene(loader.load(), 800, 600);
        scene.getStylesheets().add(Objects.requireNonNull(MusemixApplication.class.getResource("style.css")).toExternalForm());
        homeController = loader.getController();
        stage.setTitle("Musemix");
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
        System.setProperty("java.home", ".");
        launch();
    }
}