module com.billcom.drools.camtest {
    requires javafx.controls;
    requires javafx.fxml;
    requires webcam.capture;
    requires java.desktop;
    requires java.mail;
    requires activation;
    requires org.bytedeco.opencv;


    opens com.billcom.drools.camtest to javafx.fxml;
    exports com.billcom.drools.camtest;
}