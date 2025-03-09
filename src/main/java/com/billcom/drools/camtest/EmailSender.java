package com.billcom.drools.camtest;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.File;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class EmailSender {
    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

    // Executor for sending emails in background
    private static final ExecutorService emailExecutor = Executors.newSingleThreadExecutor();

    // Show email dialog and return the entered email address
    public static void showEmailDialog(Stage parentStage, File imageFile) {
        // Create the custom dialog
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Send Photo via Email");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.initOwner(parentStage);

        // Set the button types
        ButtonType sendButtonType = new ButtonType("Send", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(sendButtonType, ButtonType.CANCEL);

        // Create the email label and field
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField emailField = new TextField();
        emailField.setPromptText("Enter recipient email address");

        grid.add(new Label("Email address:"), 0, 0);
        grid.add(emailField, 1, 0);
        GridPane.setHgrow(emailField, Priority.ALWAYS);

        // Enable/Disable send button based on email validity
        Node sendButton = dialog.getDialogPane().lookupButton(sendButtonType);
        sendButton.setDisable(true);

        // Validate email as user types
        emailField.textProperty().addListener((observable, oldValue, newValue) -> {
            sendButton.setDisable(!isValidEmail(newValue));
        });

        dialog.getDialogPane().setContent(grid);

        // Request focus on the email field by default
        Platform.runLater(emailField::requestFocus);

        // Convert the result to an email address when the send button is clicked
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == sendButtonType) {
                return emailField.getText();
            }
            return null;
        });

        // Process result
        dialog.showAndWait().ifPresent(emailAddress -> {
            // Show sending progress indicator
            showProgressDialog(parentStage, emailAddress, imageFile);
        });
    }

    // Check if email is valid
    private static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    // Show progress while sending email
    private static void showProgressDialog(Stage parentStage, String emailAddress, File imageFile) {
        // Create dialog
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Sending Email");
        progressDialog.initModality(Modality.APPLICATION_MODAL);
        progressDialog.initStyle(StageStyle.UTILITY);
        progressDialog.initOwner(parentStage);
        progressDialog.setHeaderText("Sending photo to " + emailAddress);

        // Create progress indicator
        ProgressIndicator progressIndicator = new ProgressIndicator();
        VBox content = new VBox(10, progressIndicator);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20));
        progressDialog.getDialogPane().setContent(content);

        // No buttons needed during progress
        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Node cancelButton = progressDialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setVisible(false);

        // Start sending email in background
        CompletableFuture<Boolean> sendTask = CompletableFuture.supplyAsync(() -> {
            return sendEmail(emailAddress, imageFile);
        }, emailExecutor);

        // When email is sent, close progress dialog and show result
        sendTask.thenAccept(success -> {
            Platform.runLater(() -> {
                progressDialog.close();
                showResultDialog(parentStage, success, emailAddress);
            });
        });

        // Show progress dialog
        progressDialog.show();
    }

    // Show result dialog
    private static void showResultDialog(Stage parentStage, boolean success, String emailAddress) {
        Alert alert = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        alert.initOwner(parentStage);
        alert.setTitle(success ? "Email Sent" : "Email Failed");

        if (success) {
            alert.setHeaderText("Success!");
            alert.setContentText("Photo has been sent to " + emailAddress);
        } else {
            alert.setHeaderText("Failed to Send Email");
            alert.setContentText("There was a problem sending the photo to " + emailAddress +
                    ". Please check your network connection and try again.");
        }

        alert.showAndWait();
    }

    // Send email with attachment
    private static boolean sendEmail(String recipient, File attachment) {
        // NOTE: For a production app, you would use proper credential management
        // and configuration. This is a simplified example.
        final String username = "aaronborgi1@gmail.com"; // Replace with your email
        final String password = "qxzaxkmlzrdojtit";    // Replace with app password

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        try {
            // Create session with authentication
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            // Create message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setReplyTo(InternetAddress.parse(username));
            message.setSubject("Your Restaurant Photo");

            // Create the message body part
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText("Here is your photo from our restaurant. Thank you for visiting us!");

            // Create attachment part
            MimeBodyPart attachmentPart = new MimeBodyPart();
            DataSource source = new FileDataSource(attachment);
            attachmentPart.setDataHandler(new DataHandler(source));
            attachmentPart.setFileName(attachment.getName());

            // Create multipart message
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);
            multipart.addBodyPart(attachmentPart);

            // Set the content
            message.setContent(multipart);

            // Send message
            Transport.send(message);

            return true;
        } catch (Exception e) {
            System.err.println("Error sending email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Clean up resources
    public static void shutdown() {
        if (emailExecutor != null && !emailExecutor.isShutdown()) {
            emailExecutor.shutdown();
        }
    }
}