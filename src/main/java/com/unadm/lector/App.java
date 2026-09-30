package com.unadm.lector;

import com.unadm.lector.service.Settings;
import com.unadm.lector.ui.Dialogs;
import com.unadm.lector.ui.Windows;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Aplicación JavaFX.
 *
 * <p>Nombre del programa: Lector de archivos
 * <br>Autor: Luis Ángel De Santiago Guerrero
 * <br>Versión 2.0 &mdash; reescrita sobre Maven + JavaFX 21 y empaquetada en Docker.</p>
 */
public class App extends Application {

    public static final String NOMBRE = "Lector de archivos";
    public static final String VERSION = "2.0.0";

    private static final double ANCHO_MINIMO = 900;
    private static final double ALTO_MINIMO = 560;

    @Override
    public void start(Stage stage) {
        Settings.get().load();

        try {
            FXMLLoader loader = new FXMLLoader(Windows.view("MainView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1180, 740);
            Windows.applyTheme(scene);

            stage.setScene(scene);
            stage.setTitle(NOMBRE);
            stage.setMinWidth(ANCHO_MINIMO);
            stage.setMinHeight(ALTO_MINIMO);
            icono().ifPresent(stage.getIcons()::add);

            // El controlador decide si se puede cerrar (archivos sin guardar).
            com.unadm.lector.controller.MainController controller = loader.getController();
            stage.setOnCloseRequest(controller::onCloseRequest);

            stage.show();

            controller.abrirDesdeArgumentos(archivosDeLinea(getParameters().getRaw()));
        } catch (Exception e) {
            Dialogs.error(null, "No se pudo iniciar la aplicación",
                    "Ocurrió un error al cargar la ventana principal.", e);
            javafx.application.Platform.exit();
        }
    }

    @Override
    public void stop() {
        Settings.get().save();
    }

    /** Filtra los argumentos de línea de comandos que apuntan a archivos existentes. */
    private static List<Path> archivosDeLinea(List<String> args) {
        List<Path> rutas = new ArrayList<>();
        for (String arg : args) {
            try {
                Path p = Path.of(arg);
                if (Files.isRegularFile(p)) {
                    rutas.add(p);
                }
            } catch (RuntimeException ignored) {
                // Argumento que no es una ruta válida: se ignora.
            }
        }
        return rutas;
    }

    private static java.util.Optional<Image> icono() {
        try (InputStream in = App.class.getResourceAsStream("img/logounadm.png")) {
            return in == null ? java.util.Optional.empty() : java.util.Optional.of(new Image(in));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
