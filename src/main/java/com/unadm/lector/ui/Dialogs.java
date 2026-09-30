package com.unadm.lector.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Cuadros de dialogo del programa.
 *
 * <p>Reemplaza a la antigua clase {@code Message}. Las diferencias de fondo:
 * los dialogos se muestran de forma modal con {@code showAndWait} en lugar de
 * {@code show}, reciben la ventana propietaria para quedar centrados sobre
 * ella, y heredan el tema activo.</p>
 */
public final class Dialogs {

    private Dialogs() {
        // Clase utilitaria.
    }

    public static void info(Window owner, String titulo, String cabecera, String contenido) {
        mostrar(Alert.AlertType.INFORMATION, owner, titulo, cabecera, contenido);
    }

    public static void warn(Window owner, String titulo, String cabecera, String contenido) {
        mostrar(Alert.AlertType.WARNING, owner, titulo, cabecera, contenido);
    }

    public static void error(Window owner, String titulo, String cabecera, String contenido) {
        mostrar(Alert.AlertType.ERROR, owner, titulo, cabecera, contenido);
    }

    /** Error con el detalle tecnico de la excepcion en un panel desplegable. */
    public static void error(Window owner, String titulo, String cabecera, Throwable error) {
        Alert alert = base(Alert.AlertType.ERROR, owner, titulo, cabecera,
                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());

        StringWriter sw = new StringWriter();
        error.printStackTrace(new PrintWriter(sw));

        TextArea detalle = new TextArea(sw.toString());
        detalle.setEditable(false);
        detalle.setWrapText(false);
        detalle.setMaxWidth(Double.MAX_VALUE);
        detalle.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(detalle, Priority.ALWAYS);
        GridPane.setHgrow(detalle, Priority.ALWAYS);

        GridPane contenedor = new GridPane();
        contenedor.setMaxWidth(Double.MAX_VALUE);
        contenedor.add(new Label("Detalle tecnico:"), 0, 0);
        contenedor.add(detalle, 0, 1);

        alert.getDialogPane().setExpandableContent(contenedor);
        alert.showAndWait();
    }

    /** Pregunta de si/no. Devuelve {@code true} si el usuario acepto. */
    public static boolean confirm(Window owner, String titulo, String cabecera, String contenido) {
        Alert alert = base(Alert.AlertType.CONFIRMATION, owner, titulo, cabecera, contenido);
        alert.getButtonTypes().setAll(
                new ButtonType("Si", ButtonBar.ButtonData.YES),
                new ButtonType("No", ButtonBar.ButtonData.NO));

        return alert.showAndWait()
                .map(b -> b.getButtonData() == ButtonBar.ButtonData.YES)
                .orElse(false);
    }

    /** Resultado de preguntar por cambios sin guardar. */
    public enum SaveChoice { GUARDAR, DESCARTAR, CANCELAR }

    /** Pregunta que hacer con un documento modificado que esta por cerrarse. */
    public static SaveChoice askSave(Window owner, String nombreArchivo) {
        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.YES);
        ButtonType descartar = new ButtonType("No guardar", ButtonBar.ButtonData.NO);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = base(Alert.AlertType.CONFIRMATION, owner,
                "Cambios sin guardar",
                "\"" + nombreArchivo + "\" tiene cambios sin guardar.",
                "Si no los guardas, se perderan.");
        alert.getButtonTypes().setAll(guardar, descartar, cancelar);

        Optional<ButtonType> respuesta = alert.showAndWait();
        if (respuesta.isEmpty()) {
            return SaveChoice.CANCELAR;
        }
        if (respuesta.get() == guardar) {
            return SaveChoice.GUARDAR;
        }
        return respuesta.get() == descartar ? SaveChoice.DESCARTAR : SaveChoice.CANCELAR;
    }

    /** Pide un texto al usuario. Devuelve vacio si cancelo. */
    public static Optional<String> prompt(Window owner, String titulo, String cabecera,
                                          String etiqueta, String valorInicial) {
        TextInputDialog dialog = new TextInputDialog(valorInicial);
        dialog.setTitle(titulo);
        dialog.setHeaderText(cabecera);
        dialog.setContentText(etiqueta);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        Windows.applyTheme(dialog.getDialogPane().getScene());
        return dialog.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    //
    // Interno
    //
    private static void mostrar(Alert.AlertType tipo, Window owner,
                                String titulo, String cabecera, String contenido) {
        base(tipo, owner, titulo, cabecera, contenido).showAndWait();
    }

    private static Alert base(Alert.AlertType tipo, Window owner,
                              String titulo, String cabecera, String contenido) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(cabecera == null || cabecera.isBlank() ? null : cabecera);
        alert.setContentText(contenido);
        alert.setResizable(true);
        alert.getDialogPane().setMinWidth(420);
        if (owner != null) {
            alert.initOwner(owner);
        }
        Windows.applyTheme(alert.getDialogPane().getScene());
        return alert;
    }
}
