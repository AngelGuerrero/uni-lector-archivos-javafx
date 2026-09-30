package com.unadm.lector.controller;

import com.unadm.lector.App;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;

/**
 * Pantalla "Acerca de": datos del programa, del autor y del entorno.
 */
public class AboutController {

    @FXML private VBox root;
    @FXML private Label lblTitulo;
    @FXML private Label lblVersion;
    @FXML private Label lblJava;
    @FXML private Label lblJavaFX;
    @FXML private Label lblSistema;

    @FXML
    private void initialize() {
        lblTitulo.setText(App.NOMBRE);
        lblVersion.setText("Version " + App.VERSION);

        lblJava.setText(System.getProperty("java.runtime.version", "desconocida")
                + "  (" + System.getProperty("java.vm.vendor", "") + ")");
        lblJavaFX.setText(System.getProperty("javafx.runtime.version", "desconocida"));
        lblSistema.setText(System.getProperty("os.name") + " "
                + System.getProperty("os.version") + "  ("
                + System.getProperty("os.arch") + ")");
    }

    /** Copia los datos del entorno, util para reportar un problema. */
    @FXML
    private void copiarEntorno() {
        String texto = App.NOMBRE + " " + App.VERSION + "\n"
                + "Java: " + lblJava.getText() + "\n"
                + "JavaFX: " + lblJavaFX.getText() + "\n"
                + "Sistema: " + lblSistema.getText();

        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(texto);
        Clipboard.getSystemClipboard().setContent(contenido);
    }

    @FXML
    private void cerrar() {
        if (root != null && root.getScene() != null && root.getScene().getWindow() != null) {
            root.getScene().getWindow().hide();
        }
    }
}
