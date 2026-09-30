package com.unadm.lector.controller;

import com.unadm.lector.ui.DocumentTab;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Ventana de busqueda y reemplazo.
 *
 * <p>No es modal: se queda abierta mientras se navega por el documento.
 * Trabaja siempre sobre la pestana activa de la ventana principal.</p>
 */
public class FindReplaceController {

    @FXML private VBox root;
    @FXML private TextField txtBuscar;
    @FXML private TextField txtReemplazar;
    @FXML private CheckBox chkMayusculas;
    @FXML private CheckBox chkPalabraCompleta;
    @FXML private CheckBox chkRegex;
    @FXML private CheckBox chkCircular;
    @FXML private Label lblResultado;

    private MainController main;

    @FXML
    private void initialize() {
        // Enter busca la siguiente coincidencia; Escape cierra la ventana.
        txtBuscar.setOnAction(e -> buscarSiguiente());
        txtReemplazar.setOnAction(e -> reemplazar());

        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cerrar();
            }
        });

        // "Palabra completa" y "expresion regular" son formas distintas de
        // interpretar el patron: activar una desactiva la otra.
        chkRegex.selectedProperty().addListener((obs, v, n) -> {
            if (n) {
                chkPalabraCompleta.setSelected(false);
            }
            chkPalabraCompleta.setDisable(n);
        });

        txtBuscar.textProperty().addListener((obs, v, n) -> resultado(""));
    }

    /** La ventana principal se inyecta al abrir esta pantalla. */
    public void enlazar(MainController main) {
        this.main = main;
    }

    //
    // Acciones
    //
    @FXML
    private void buscarSiguiente() {
        buscar(true);
    }

    @FXML
    private void buscarAnterior() {
        buscar(false);
    }

    private void buscar(boolean haciaAdelante) {
        editor().ifPresent(editor -> {
            Optional<Pattern> patron = compilar();
            if (patron.isEmpty()) {
                return;
            }

            String texto = editor.getText();
            Matcher m = patron.get().matcher(texto);

            IndexRange seleccion = editor.getSelection();
            int desde = haciaAdelante ? seleccion.getEnd() : seleccion.getStart();

            Optional<IndexRange> hallazgo = haciaAdelante
                    ? siguienteDesde(m, desde, texto.length())
                    : anteriorAntesDe(m, desde);

            if (hallazgo.isEmpty() && chkCircular.isSelected()) {
                m.reset();
                hallazgo = haciaAdelante
                        ? siguienteDesde(m, 0, texto.length())
                        : anteriorAntesDe(m, texto.length());
                if (hallazgo.isPresent()) {
                    resultado(haciaAdelante
                            ? "Se continuo desde el principio."
                            : "Se continuo desde el final.");
                }
            }

            if (hallazgo.isEmpty()) {
                resultado("Sin coincidencias.");
                return;
            }

            IndexRange r = hallazgo.get();
            editor.requestFocus();
            editor.selectRange(r.getStart(), r.getEnd());
            if (lblResultado.getText().isBlank()) {
                resultado("Coincidencia en la posicion " + r.getStart() + ".");
            }
        });
    }

    private static Optional<IndexRange> siguienteDesde(Matcher m, int desde, int limite) {
        int inicio = Math.clamp(desde, 0, limite);
        return m.find(inicio) ? Optional.of(new IndexRange(m.start(), m.end())) : Optional.empty();
    }

    private static Optional<IndexRange> anteriorAntesDe(Matcher m, int limite) {
        // Matcher solo avanza, asi que se recorre desde el inicio guardando la
        // ultima coincidencia que termina antes del punto de partida.
        // find() adelanta solo una posicion tras una coincidencia vacia, de modo
        // que el bucle siempre termina.
        IndexRange ultima = null;
        m.reset();
        while (m.find()) {
            if (m.end() > limite) {
                break;
            }
            ultima = new IndexRange(m.start(), m.end());
        }
        return Optional.ofNullable(ultima);
    }

    @FXML
    private void contar() {
        editor().ifPresent(editor -> compilar().ifPresent(patron -> {
            Matcher m = patron.matcher(editor.getText());
            int total = 0;
            while (m.find()) {
                total++;
            }
            resultado(total == 0 ? "Sin coincidencias."
                    : total + (total == 1 ? " coincidencia." : " coincidencias."));
        }));
    }

    @FXML
    private void reemplazar() {
        editor().ifPresent(editor -> {
            Optional<Pattern> patron = compilar();
            if (patron.isEmpty()) {
                return;
            }

            IndexRange seleccion = editor.getSelection();
            String seleccionado = editor.getSelectedText();

            // Solo se reemplaza si lo que esta seleccionado ya es una coincidencia;
            // si no, primero se busca, como hacen los editores habituales.
            if (!seleccionado.isEmpty() && patron.get().matcher(seleccionado).matches()) {
                editor.replaceText(seleccion, reemplazoLiteral());
                resultado("Reemplazado.");
            }
            buscarSiguiente();
        });
    }

    @FXML
    private void reemplazarTodo() {
        editor().ifPresent(editor -> compilar().ifPresent(patron -> {
            String texto = editor.getText();
            Matcher m = patron.matcher(texto);

            StringBuilder sb = new StringBuilder();
            int total = 0;
            // El reemplazo se trata siempre como literal, incluso en modo regex:
            // asi un "$" o una "\" en el cuadro no rompen la operacion.
            String reemplazo = Matcher.quoteReplacement(reemplazoLiteral());
            while (m.find()) {
                m.appendReplacement(sb, reemplazo);
                total++;
            }
            m.appendTail(sb);

            if (total == 0) {
                resultado("Sin coincidencias.");
                return;
            }

            int posicion = editor.getCaretPosition();
            editor.setText(sb.toString());
            editor.positionCaret(Math.min(posicion, sb.length()));
            resultado(total + (total == 1 ? " reemplazo hecho." : " reemplazos hechos."));
        }));
    }

    @FXML
    private void cerrar() {
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            root.getScene().getWindow().hide();
        }
    }

    //
    // Interno
    //
    private String reemplazoLiteral() {
        return txtReemplazar.getText() == null ? "" : txtReemplazar.getText();
    }

    /** Construye el patron segun las casillas marcadas. */
    private Optional<Pattern> compilar() {
        String patron = txtBuscar.getText();
        if (patron == null || patron.isEmpty()) {
            resultado("Escribe algo que buscar.");
            return Optional.empty();
        }

        String expresion = chkRegex.isSelected() ? patron : Pattern.quote(patron);
        if (chkPalabraCompleta.isSelected() && !chkRegex.isSelected()) {
            expresion = "\\b" + expresion + "\\b";
        }

        int banderas = chkMayusculas.isSelected() ? 0
                : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

        try {
            return Optional.of(Pattern.compile(expresion, banderas));
        } catch (PatternSyntaxException e) {
            resultado("Expresion regular no valida: " + e.getDescription());
            return Optional.empty();
        }
    }

    private Optional<TextArea> editor() {
        if (main == null) {
            return Optional.empty();
        }
        Optional<DocumentTab> doc = main.documentoActivo();
        if (doc.isEmpty()) {
            resultado("No hay ningun documento abierto.");
            return Optional.empty();
        }
        return doc.map(DocumentTab::getEditor);
    }

    private void resultado(String mensaje) {
        lblResultado.setText(mensaje);
    }
}
