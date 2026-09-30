package com.unadm.lector.controller;

import com.unadm.lector.service.Settings;
import com.unadm.lector.ui.Dialogs;
import com.unadm.lector.ui.Windows;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de preferencias.
 *
 * <p>Los cambios se aplican en vivo sobre una copia de trabajo; el boton
 * Cancelar restaura los valores que habia al abrir la ventana.</p>
 */
public class PreferencesController {

    private static final String MUESTRA =
            "El veloz murcielago hindu comia feliz cardillo y kiwi.\n"
            + "La cigueena tocaba el saxofon detras del palenque de paja.\n"
            + "0123456789  ()[]{}  ->  <=  !=  ==  ;  #  @  naño  año";

    @FXML private VBox root;
    @FXML private ComboBox<Settings.Theme> cbTema;
    @FXML private ComboBox<String> cbFuente;
    @FXML private Spinner<Integer> spTamano;
    @FXML private ComboBox<Charset> cbCodificacion;
    @FXML private CheckBox chkAjusteLinea;
    @FXML private CheckBox chkNumerosLinea;
    @FXML private CheckBox chkDetectarCodificacion;
    @FXML private Spinner<Integer> spRecientes;
    @FXML private TextArea txtMuestra;
    @FXML private Label lblRutaConfig;

    private MainController main;

    /** Valores con los que se abrio la ventana, para poder deshacer. */
    private Settings.Theme temaOriginal;
    private String fuenteOriginal;
    private int tamanoOriginal;
    private String codificacionOriginal;
    private boolean ajusteOriginal;
    private boolean numerosOriginal;
    private boolean detectarOriginal;
    private int recientesOriginal;

    @FXML
    private void initialize() {
        Settings s = Settings.get();

        guardarOriginales(s);
        poblarControles(s);
        escuchar(s);

        lblRutaConfig.setText(Settings.configFile().toString());
        actualizarMuestra();
    }

    /** La ventana principal se inyecta para poder refrescar los documentos abiertos. */
    public void enlazar(MainController main) {
        this.main = main;
    }

    //
    // Construccion
    //
    private void guardarOriginales(Settings s) {
        temaOriginal = s.getTheme();
        fuenteOriginal = s.fontFamilyProperty().get();
        tamanoOriginal = s.fontSizeProperty().get();
        codificacionOriginal = s.defaultCharsetProperty().get();
        ajusteOriginal = s.wrapTextProperty().get();
        numerosOriginal = s.showLineNumbersProperty().get();
        detectarOriginal = s.detectCharsetProperty().get();
        recientesOriginal = s.maxRecentFilesProperty().get();
    }

    private void poblarControles(Settings s) {
        cbTema.setItems(FXCollections.observableArrayList(Settings.Theme.values()));
        cbTema.getSelectionModel().select(s.getTheme());

        cbFuente.setItems(FXCollections.observableArrayList(fuentesDisponibles()));
        cbFuente.getSelectionModel().select(s.fontFamilyProperty().get());

        spTamano.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                Settings.TAMANO_FUENTE_MIN, Settings.TAMANO_FUENTE_MAX, s.fontSizeProperty().get()));
        spTamano.setEditable(true);

        cbCodificacion.setItems(FXCollections.observableArrayList(Settings.CODIFICACIONES));
        cbCodificacion.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Charset c) { return c == null ? "" : c.displayName(); }
            @Override public Charset fromString(String v) { return Settings.charsetOrUtf8(v); }
        });
        cbCodificacion.getSelectionModel().select(s.getDefaultCharset());

        chkAjusteLinea.setSelected(s.wrapTextProperty().get());
        chkNumerosLinea.setSelected(s.showLineNumbersProperty().get());
        chkDetectarCodificacion.setSelected(s.detectCharsetProperty().get());

        spRecientes.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                0, 50, s.maxRecentFilesProperty().get()));
        spRecientes.setEditable(true);

        txtMuestra.setText(MUESTRA);
        txtMuestra.setEditable(false);
    }

    private void escuchar(Settings s) {
        cbTema.getSelectionModel().selectedItemProperty().addListener((obs, v, n) -> {
            if (n != null) {
                s.setTheme(n);
                Windows.refreshTheme();
            }
        });

        cbFuente.getSelectionModel().selectedItemProperty().addListener((obs, v, n) -> {
            if (n != null) {
                s.fontFamilyProperty().set(n);
                actualizarMuestra();
            }
        });

        spTamano.valueProperty().addListener((obs, v, n) -> {
            if (n != null) {
                s.fontSizeProperty().set(n);
                actualizarMuestra();
            }
        });

        cbCodificacion.getSelectionModel().selectedItemProperty().addListener((obs, v, n) -> {
            if (n != null) {
                s.setDefaultCharset(n);
            }
        });

        chkAjusteLinea.selectedProperty().addListener((obs, v, n) -> s.wrapTextProperty().set(n));
        chkNumerosLinea.selectedProperty().addListener((obs, v, n) -> s.showLineNumbersProperty().set(n));
        chkDetectarCodificacion.selectedProperty().addListener((obs, v, n) -> s.detectCharsetProperty().set(n));

        spRecientes.valueProperty().addListener((obs, v, n) -> {
            if (n != null) {
                s.maxRecentFilesProperty().set(n);
                // Recortar la lista de inmediato si el nuevo maximo es menor.
                while (s.getRecentFiles().size() > n) {
                    s.getRecentFiles().remove(s.getRecentFiles().size() - 1);
                }
            }
        });
    }

    /**
     * Tipografias monoespaciadas que existen en el sistema, mas las preferidas
     * que no esten instaladas, para no perder el valor ya configurado.
     */
    private static List<String> fuentesDisponibles() {
        List<String> instaladas = Font.getFamilies();
        List<String> resultado = new ArrayList<>();

        for (String preferida : Settings.TIPOGRAFIAS) {
            if (instaladas.contains(preferida) && !resultado.contains(preferida)) {
                resultado.add(preferida);
            }
        }
        // "Monospaced" es un alias logico que la JVM siempre resuelve.
        if (!resultado.contains("Monospaced")) {
            resultado.add("Monospaced");
        }
        for (String familia : instaladas) {
            if (!resultado.contains(familia)) {
                resultado.add(familia);
            }
        }
        return resultado;
    }

    private void actualizarMuestra() {
        Settings s = Settings.get();
        txtMuestra.setStyle("-fx-font-family: \"" + s.fontFamilyProperty().get().replace("\"", "") + "\";"
                + "-fx-font-size: " + s.fontSizeProperty().get() + "px;");
    }

    //
    // Acciones
    //
    @FXML
    private void restaurarValores() {
        boolean seguro = Dialogs.confirm(ventana(), "Restaurar",
                "Restablecer las preferencias",
                "Se volvera a los valores de fabrica. La lista de archivos recientes no se toca.");
        if (!seguro) {
            return;
        }
        Settings.get().restoreDefaults();
        reflejar();
        aplicarYPropagar();
    }

    @FXML
    private void aceptar() {
        Settings.get().save();
        aplicarYPropagar();
        cerrar();
    }

    @FXML
    private void cancelar() {
        Settings s = Settings.get();
        s.setTheme(temaOriginal);
        s.fontFamilyProperty().set(fuenteOriginal);
        s.fontSizeProperty().set(tamanoOriginal);
        s.defaultCharsetProperty().set(codificacionOriginal);
        s.wrapTextProperty().set(ajusteOriginal);
        s.showLineNumbersProperty().set(numerosOriginal);
        s.detectCharsetProperty().set(detectarOriginal);
        s.maxRecentFilesProperty().set(recientesOriginal);

        aplicarYPropagar();
        cerrar();
    }

    /** Vuelve a leer las preferencias hacia los controles, sin disparar bucles. */
    private void reflejar() {
        Settings s = Settings.get();
        cbTema.getSelectionModel().select(s.getTheme());
        cbFuente.getSelectionModel().select(s.fontFamilyProperty().get());
        spTamano.getValueFactory().setValue(s.fontSizeProperty().get());
        cbCodificacion.getSelectionModel().select(s.getDefaultCharset());
        chkAjusteLinea.setSelected(s.wrapTextProperty().get());
        chkNumerosLinea.setSelected(s.showLineNumbersProperty().get());
        chkDetectarCodificacion.setSelected(s.detectCharsetProperty().get());
        spRecientes.getValueFactory().setValue(s.maxRecentFilesProperty().get());
        actualizarMuestra();
    }

    private void aplicarYPropagar() {
        Windows.refreshTheme();
        if (main != null) {
            main.refrescarDocumentos();
            main.documentoActivo().ifPresent(d -> d.setWrapText(Settings.get().wrapTextProperty().get()));
        }
    }

    private void cerrar() {
        if (ventana() != null) {
            ventana().hide();
        }
    }

    private javafx.stage.Window ventana() {
        return root == null || root.getScene() == null ? null : root.getScene().getWindow();
    }
}
