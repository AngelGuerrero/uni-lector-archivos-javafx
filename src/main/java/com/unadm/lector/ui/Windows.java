package com.unadm.lector.ui;

import com.unadm.lector.App;
import com.unadm.lector.service.Settings;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Carga de vistas FXML y aplicacion del tema a las ventanas.
 *
 * <p>Centraliza el patron que antes estaba repetido dentro del controlador:
 * crear un {@code Stage}, cargar el FXML, fijar la escena y mostrarla.</p>
 */
public final class Windows {

    /** Ventanas secundarias abiertas, para poder retematizarlas al vuelo. */
    private static final List<Scene> ESCENAS = new ArrayList<>();

    private Windows() {
        // Clase utilitaria.
    }

    /** Localiza un FXML dentro del paquete de recursos de la aplicacion. */
    public static URL view(String nombre) {
        URL url = App.class.getResource("view/" + nombre);
        if (url == null) {
            throw new IllegalStateException("No se encontro la vista: view/" + nombre);
        }
        return url;
    }

    /**
     * Abre una vista en una ventana nueva.
     *
     * @param owner   ventana padre; la nueva queda centrada sobre ella
     * @param fxml    nombre del archivo FXML dentro de {@code view/}
     * @param titulo  titulo de la ventana
     * @param modal   si bloquea la interaccion con la ventana padre
     * @param inicial accion que recibe el controlador antes de mostrar la ventana
     * @param <C>     tipo del controlador de la vista
     * @return el {@code Stage} ya visible
     */
    public static <C> Stage open(Window owner, String fxml, String titulo, boolean modal,
                                 java.util.function.Consumer<C> inicial) {
        try {
            FXMLLoader loader = new FXMLLoader(view(fxml));
            Parent root = loader.load();

            C controller = loader.getController();
            if (inicial != null && controller != null) {
                inicial.accept(controller);
            }

            Stage stage = new Stage();
            Scene scene = new Scene(root);
            applyTheme(scene);

            stage.setScene(scene);
            stage.setTitle(titulo);
            if (owner != null) {
                stage.initOwner(owner);
            }
            if (modal) {
                stage.initModality(Modality.WINDOW_MODAL);
            }
            icono().ifPresent(stage.getIcons()::add);

            // La escena deja de seguir al tema cuando se cierra la ventana.
            stage.setOnHidden(e -> ESCENAS.remove(scene));

            stage.show();
            return stage;
        } catch (Exception e) {
            Dialogs.error(owner, "Error", "No se pudo abrir la ventana \"" + titulo + "\".", e);
            return null;
        }
    }

    /** Aplica la hoja de estilo del tema activo y registra la escena para futuros cambios. */
    public static void applyTheme(Scene scene) {
        if (scene == null) {
            return;
        }
        if (!ESCENAS.contains(scene)) {
            ESCENAS.add(scene);
        }
        aplicar(scene, Settings.get().getTheme());
    }

    /** Cambia el tema de todas las ventanas abiertas. */
    public static void refreshTheme() {
        Settings.Theme tema = Settings.get().getTheme();
        for (Scene scene : List.copyOf(ESCENAS)) {
            aplicar(scene, tema);
        }
    }

    private static void aplicar(Scene scene, Settings.Theme tema) {
        // La hoja base trae la estructura; la del tema solo redefine colores.
        // La tipografia del editor la aplica cada DocumentTab por su cuenta,
        // para no volver monoespaciada toda la interfaz.
        scene.getStylesheets().setAll(recurso("css/base.css"), recurso(tema.hoja()));
    }

    private static String recurso(String ruta) {
        URL url = App.class.getResource(ruta);
        if (url == null) {
            throw new IllegalStateException("No se encontro el recurso: " + ruta);
        }
        return url.toExternalForm();
    }

    static java.util.Optional<Image> icono() {
        try (InputStream in = App.class.getResourceAsStream("img/logounadm.png")) {
            return in == null ? java.util.Optional.empty() : java.util.Optional.of(new Image(in));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }
}
