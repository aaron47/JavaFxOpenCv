package com.billcom.drools.camtest;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.effect.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

public class CameraController {
    @FXML
    private ImageView cameraView;
    @FXML
    private ImageView processedImageView;
    @FXML
    private Button saveBtn;
    @FXML
    private Button sepiaFilterBtn;
    @FXML
    private Button bwFilterBtn;
    @FXML
    private Button blurFilterBtn;
    @FXML
    private Button emailBtn;
    @FXML
    private Button switchCameraBtn;
    @FXML
    private BorderPane cameraPanel;

    private VideoCapture capture;
    private CascadeClassifier faceDetector;
    private boolean cameraActive = false;
    private ScheduledExecutorService cameraTimer;
    private Effect currentFilter = null;
    private File lastSavedImageFile;
    private int cameraIndex = 0;

    // face detection intervals
    private static final int FACE_DETECTION_INTERVAL = 10;
    private int frameCounter = 0;

    @FXML
    public void initialize() {
        startCamera();
        loadFaceDetectionModel();
        cameraView.fitWidthProperty().bind(cameraPanel.widthProperty());
        cameraView.fitHeightProperty().bind(cameraPanel.heightProperty());
    }

    private void startCamera() {
        if (!cameraActive) {
            // Try to open the camera
            capture = new VideoCapture();
            capture.open(cameraIndex);

            if (capture.isOpened()) {
                cameraActive = true;

                // Start the camera capture thread
                cameraTimer = Executors.newSingleThreadScheduledExecutor();
                cameraTimer.scheduleAtFixedRate(this::updateFrame, 0, 100, TimeUnit.MILLISECONDS);
                System.out.println("Camera started successfully");
            } else {
                System.err.println("Failed to open camera with index: " + cameraIndex);
                // Try default camera as fallback
                capture.open(0);
                if (capture.isOpened()) {
                    cameraActive = true;
                    cameraTimer = Executors.newSingleThreadScheduledExecutor();
                    cameraTimer.scheduleAtFixedRate(this::updateFrame, 0, 33, TimeUnit.MILLISECONDS);
                    System.out.println("Default camera started as fallback");
                } else {
                    System.err.println("Could not start any camera");
                }
            }
        }
    }

    private void loadFaceDetectionModel() {
        try {
            // Try to find the cascade file in several locations
            File cascadeFile = new File("haarcascade_frontalface_alt.xml");
            if (!cascadeFile.exists()) {
                cascadeFile = new File("src/main/resources/haarcascade_frontalface_alt.xml");
            }

            if (cascadeFile.exists()) {
                faceDetector = new CascadeClassifier(cascadeFile.getAbsolutePath());
                if (faceDetector.empty()) {
                    System.err.println("Failed to load face detection model");
                    faceDetector = null;
                } else {
                    System.out.println("Face detection model loaded successfully");
                }
            } else {
                System.err.println("Cascade file not found");
                faceDetector = null;
            }
        } catch (Exception e) {
            System.err.println("Error loading face detection model: " + e.getMessage());
            e.printStackTrace();
            faceDetector = null;
        }
    }

    private void updateFrame() {
        Mat frame = new Mat();

        // Try to read a new frame
        boolean frameRead = capture.read(frame);

        if (frameRead && !frame.empty()) {
            // Convert the frame from BGR to RGB
            Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2RGB);

            // Detect faces if the face detector is available
            if (faceDetector != null && !faceDetector.empty() && (frameCounter % FACE_DETECTION_INTERVAL == 0)) {
                detectFaces(frame);
            }
            this.frameCounter++;

            // Convert the OpenCV Mat to a JavaFX WritableImage
            WritableImage writableImage = convertToFxImage(frame);

            // Update the ImageView on the JavaFX Application Thread
            javafx.application.Platform.runLater(() -> {
                cameraView.setImage(writableImage);
                if (currentFilter != null) {
                    cameraView.setEffect(currentFilter);
                } else {
                    cameraView.setEffect(null);
                }
            });
        }
    }

    private void detectFaces(Mat frame) {
        Mat grayFrame = new Mat();
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_RGB2GRAY);
        Imgproc.equalizeHist(grayFrame, grayFrame);

        MatOfRect faceDetections = new MatOfRect();
        faceDetector.detectMultiScale(
                grayFrame,
                faceDetections,
                1.1,
                2,
                0 | org.opencv.objdetect.Objdetect.CASCADE_SCALE_IMAGE,
                new Size(30, 30)
        );

        Rect[] facesArray = faceDetections.toArray();
        for (Rect face : facesArray) {
//            Imgproc.rectangle(
//                    frame,
//                    new Point(face.x, face.y),
//                    new Point(face.x + face.width, face.y + face.height),
//                    new Scalar(0, 255, 0),
//                    3
//            );
        }
    }

    @FXML
    private void onCapture() {
        if (cameraView.getImage() != null) {
            Image snapshot = cameraView.getImage();
            Image originalLogo = new Image("file:restaurant_logo.png"); // Load the logo image
            Image border = new Image("file:border.png"); // Load the border image

            // Create a canvas to draw both images
            javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(
                    snapshot.getWidth(), snapshot.getHeight());
            javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

            // Draw the original image
            gc.drawImage(snapshot, 0, 0);

            // Apply filter if needed
            if (currentFilter != null) {
                gc.setEffect(currentFilter);
                gc.drawImage(snapshot, 0, 0);
                gc.setEffect(null); // Reset effect for logo
            }

            gc.drawImage(border, 0, 0, snapshot.getWidth(), snapshot.getHeight());

            // Scale the logo to a smaller size (adjust these values as needed)
            double logoWidth = snapshot.getWidth() * 0.15; // 15% of the image width
            double logoHeight = originalLogo.getHeight() * (logoWidth / originalLogo.getWidth()); // Keep aspect ratio
            // Calculate position for the resized logo in bottom right
            double logoX = snapshot.getWidth() - logoWidth - 10; // 10px padding
            double logoY = snapshot.getHeight() - logoHeight - 10; // 10px padding

            // Draw the resized logo at the bottom right
            gc.drawImage(originalLogo, logoX, logoY, logoWidth, logoHeight);

            // Capture the final image
            WritableImage finalImage = new WritableImage(
                    (int) snapshot.getWidth(), (int) snapshot.getHeight());
            canvas.snapshot(null, finalImage);

            processedImageView.setImage(finalImage);
            saveBtn.setDisable(false);
            System.out.println("Image captured with resized logo");
        }
    }

    @FXML
    private void onSave() {
        if (processedImageView.getImage() != null) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            lastSavedImageFile = new File("photo_" + timestamp + ".png");

            try {
                BufferedImage bufferedImage = convertFromFxImage((Image) processedImageView.getImage());
                ImageIO.write(bufferedImage, "png", lastSavedImageFile);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Saved Image");
                alert.setHeaderText("Image saved successfully");
                alert.setContentText("Image saved to " + lastSavedImageFile.getAbsolutePath());
                alert.showAndWait();

                System.out.println("Image saved: " + lastSavedImageFile.getAbsolutePath());
                emailBtn.setDisable(false);
            } catch (IOException e) {
                System.err.println("Error saving image: " + e.getMessage());
                e.printStackTrace();

                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Save Error");
                alert.setHeaderText("Failed to save image");
                alert.setContentText("Error: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    @FXML
    private void onEmailPhoto() {
        if (lastSavedImageFile != null && lastSavedImageFile.exists()) {
            Stage stage = (Stage) cameraView.getScene().getWindow();
            EmailSender.showEmailDialog(stage, lastSavedImageFile);
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Image Not Found");
            alert.setContentText("The image file could not be found. Please try taking and saving another photo.");
            alert.showAndWait();
        }
    }

    @FXML
    private void onSwitchCamera() {
        if (cameraActive) {
            // Stop current camera
            if (cameraTimer != null && !cameraTimer.isShutdown()) {
                cameraTimer.shutdown();
                try {
                    cameraTimer.awaitTermination(33, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    System.err.println("Error shutting down camera timer: " + e.getMessage());
                }
            }

            if (capture != null) {
                capture.release();
            }

            cameraActive = false;
        }

        // Switch to next camera
        cameraIndex = (cameraIndex + 1) % 2;
        System.out.println("Switching to camera index: " + cameraIndex);

        // Start new camera
        startCamera();
    }

    @FXML
    private void onSepiaFilterSelected() {
        currentFilter = new SepiaTone(0.7);
        System.out.println("Sepia filter applied");
    }

    @FXML
    private void onBWFilterSelected() {
        ColorAdjust bwAdjust = new ColorAdjust();
        bwAdjust.setSaturation(-1.0);
        currentFilter = bwAdjust;
        System.out.println("B&W filter applied");
    }

    @FXML
    private void onBlurFilterSelected() {
        currentFilter = new GaussianBlur(10);
        System.out.println("Blur filter applied");
    }

    @FXML
    private void onClearFilterSelected() {
        currentFilter = null;
        System.out.println("Filters cleared");
    }

    private WritableImage convertToFxImage(Mat mat) {
        // Get matrix dimensions
        int width = mat.cols();
        int height = mat.rows();

        // Create buffered image
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);

        // Get the data array from the matrix
        byte[] data = new byte[width * height * (int) mat.elemSize()];
        mat.get(0, 0, data);

        // Fill the buffered image
        if (mat.channels() == 3) {
            // For BGR/RGB images
            int[] intData = new int[width * height];
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    int index = i * width + j;
                    int bufferIndex = index * 3;
                    int r = data[bufferIndex + 0] & 0xFF; // R
                    int g = data[bufferIndex + 1] & 0xFF; // G
                    int b = data[bufferIndex + 2] & 0xFF; // B
                    intData[index] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
            bufferedImage.setRGB(0, 0, width, height, intData, 0, width);
        }

        // Convert to JavaFX image
        WritableImage writableImage = new WritableImage(width, height);
        PixelWriter pixelWriter = writableImage.getPixelWriter();

        // Copy pixel data
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixelWriter.setArgb(x, y, bufferedImage.getRGB(x, y));
            }
        }

        return writableImage;
    }

    private BufferedImage convertFromFxImage(Image fxImage) {
        int width = (int) fxImage.getWidth();
        int height = (int) fxImage.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        PixelReader pixelReader = fxImage.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bufferedImage.setRGB(x, y, pixelReader.getArgb(x, y));
            }
        }

        return bufferedImage;
    }

    public void shutdown() {
        if (cameraTimer != null && !cameraTimer.isShutdown()) {
            cameraTimer.shutdown();
            try {
                cameraTimer.awaitTermination(33, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                System.err.println("Error shutting down camera timer: " + e.getMessage());
            }
        }

        if (capture != null && capture.isOpened()) {
            capture.release();
        }

        EmailSender.shutdown();
        System.out.println("Camera resources released");
        System.out.println("Email resources released");
    }
}