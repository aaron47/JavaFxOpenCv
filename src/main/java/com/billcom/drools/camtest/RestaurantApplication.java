package com.billcom.drools.camtest;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;

public class RestaurantApplication extends Application {

    static {
        Loader.load(opencv_java.class);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("camera-view.fxml"));
        Parent root = loader.load();

        CameraController controller = loader.getController();

        primaryStage.setTitle("Restaurant Camera App");
        primaryStage.setScene(new Scene(root, 1000, 600));
        primaryStage.setOnCloseRequest(e -> controller.shutdown());
        primaryStage.setFullScreen(true);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}