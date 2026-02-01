module com.uniqueapps.musemixe {
    requires javafx.fxml;
    requires atlantafx.base;
    requires java.desktop;
    requires java.xml.crypto;
    requires com.google.gson;

    opens com.uniqueapps.musemixe to javafx.fxml, com.google.gson;
    exports com.uniqueapps.musemixe;
}