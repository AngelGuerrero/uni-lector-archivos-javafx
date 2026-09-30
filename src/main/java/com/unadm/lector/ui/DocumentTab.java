package com.unadm.lector.ui;

import com.unadm.lector.service.FileService;
import com.unadm.lector.service.Settings;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Una pestana del editor: representa un documento abierto.
 *
 * <p>Combina el area de texto con una canaleta de numeros de linea que se
 * desplaza en sincronia, y lleva el control de si el contenido cambio
 * respecto a lo que hay en disco.</p>
 */
public class DocumentTab extends Tab {

    private static int contadorSinTitulo = 0;

    private final TextArea editor = new TextArea();
    private final TextArea canaleta = new TextArea();
    private final BorderPane contenido = new BorderPane();

    private final ObjectProperty<Path> path = new SimpleObjectProperty<>();
    private final ObjectProperty<Charset> charset = new SimpleObjectProperty<>(StandardCharsets.UTF_8);
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private final BooleanProperty hasBom = new SimpleBooleanProperty(false);
    private final IntegerProperty caretLine = new SimpleIntegerProperty(1);
    private final IntegerProperty caretColumn = new SimpleIntegerProperty(1);

    /** Tamano en disco la ultima vez que se leyo o guardo. */
    private long bytesEnDisco = 0;

    /** Contenido tal como esta en disco; sirve para saber si hubo cambios reales. */
    private String contenidoGuardado = "";

    /** Numero de lineas dibujado en la canaleta, para no reconstruirla sin necesidad. */
    private int lineasDibujadas = -1;

    public DocumentTab() {
        this(null, "", StandardCharsets.UTF_8, false, 0);
    }

    public DocumentTab(Path path, String texto, Charset charset, boolean hasBom, long bytes) {
        this.path.set(path);
        this.charset.set(charset == null ? StandardCharsets.UTF_8 : charset);
        this.hasBom.set(hasBom);
        this.bytesEnDisco = bytes;
        this.contenidoGuardado = texto == null ? "" : texto;

        configurarEditor();
        configurarCanaleta();
        configurarLayout();
        configurarTitulo(path);
        configurarEscuchas();

        // El listener compara contra contenidoGuardado, que ya vale lo mismo:
        // la pestana nace sin marca de modificado.
        editor.setText(contenidoGuardado);

        aplicarPreferencias();
        setContent(contenido);
    }

    //
    // Construccion
    //
    private void configurarEditor() {
        editor.getStyleClass().add("editor");
        editor.setWrapText(Settings.get().wrapTextProperty().get());
    }

    private void configurarCanaleta() {
        canaleta.getStyleClass().add("gutter");
        canaleta.setEditable(false);
        canaleta.setFocusTraversable(false);
        canaleta.setWrapText(false);
        canaleta.setMouseTransparent(true);
        canaleta.setMinWidth(48);
        canaleta.setPrefWidth(48);
        canaleta.setMaxWidth(48);
    }

    private void configurarLayout() {
        contenido.getStyleClass().add("document-pane");
        contenido.setCenter(editor);
    }

    private void configurarTitulo(Path path) {
        // El nombre provisional se fija una sola vez, al crear la pestana: si se
        // leyera del contador estatico mas tarde, otra pestana ya lo habria movido.
        this.nombreProvisional = path == null ? "Sin titulo " + (++contadorSinTitulo) : null;

        // El titulo se recalcula por enlace: nombre + asterisco si hay cambios.
        textProperty().bind(Bindings.createStringBinding(
                () -> {
                    Path p = this.path.get();
                    String base = p != null ? p.getFileName().toString() : nombreProvisional;
                    return dirty.get() ? base + " *" : base;
                },
                this.path, dirty));

        // Tooltip con la ruta completa.
        javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip();
        tip.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    Path p = this.path.get();
                    return p != null ? p.toAbsolutePath().toString() : "Documento nuevo, aun sin guardar";
                },
                this.path));
        setTooltip(tip);
    }

    private String nombreProvisional;

    /** Escuchas registradas en el singleton de preferencias, para poder soltarlas al cerrar. */
    private final javafx.beans.value.ChangeListener<Object> alCambiarFuente = (obs, v, n) -> aplicarFuente();
    private final javafx.beans.value.ChangeListener<Boolean> alCambiarNumeros =
            (obs, v, n) -> actualizarVisibilidadCanaleta();

    private void configurarEscuchas() {
        editor.textProperty().addListener((obs, viejo, nuevo) -> {
            dirty.set(!java.util.Objects.equals(nuevo, contenidoGuardado));
            actualizarCanaleta();
        });

        editor.caretPositionProperty().addListener((obs, viejo, nuevo) -> actualizarPosicionCursor());

        // La canaleta sigue el desplazamiento vertical del editor.
        editor.scrollTopProperty().addListener((obs, viejo, nuevo) -> canaleta.setScrollTop(nuevo.doubleValue()));

        Settings s = Settings.get();
        s.fontFamilyProperty().addListener(alCambiarFuente);
        s.fontSizeProperty().addListener(alCambiarFuente);
        s.showLineNumbersProperty().addListener(alCambiarNumeros);

        // Settings es un singleton que vive toda la sesion: si la pestana no se
        // da de baja de sus propiedades, queda retenida para siempre.
        setOnClosed(e -> liberar());
    }

    /** Suelta las escuchas sobre las preferencias globales. */
    public void liberar() {
        Settings s = Settings.get();
        s.fontFamilyProperty().removeListener(alCambiarFuente);
        s.fontSizeProperty().removeListener(alCambiarFuente);
        s.showLineNumbersProperty().removeListener(alCambiarNumeros);
    }

    //
    // Preferencias
    //
    public final void aplicarPreferencias() {
        aplicarFuente();
        setWrapText(Settings.get().wrapTextProperty().get());
    }

    private void aplicarFuente() {
        Settings s = Settings.get();
        String estilo = "-fx-font-family: \"" + s.fontFamilyProperty().get().replace("\"", "") + "\";"
                + "-fx-font-size: " + s.fontSizeProperty().get() + "px;";
        editor.setStyle(estilo);
        canaleta.setStyle(estilo);
        // Reservar mas ancho conforme crece la fuente, si no los numeros se cortan.
        ajustarAnchoCanaleta(Math.max(lineasDibujadas, 1));
    }

    public void setWrapText(boolean wrap) {
        editor.setWrapText(wrap);
        // Con ajuste de linea una linea logica ocupa varias visuales, asi que
        // los numeros dejarian de coincidir: la canaleta se oculta.
        actualizarVisibilidadCanaleta();
    }

    public boolean isWrapText() {
        return editor.isWrapText();
    }

    private void actualizarVisibilidadCanaleta() {
        boolean visible = Settings.get().showLineNumbersProperty().get() && !editor.isWrapText();
        contenido.setLeft(visible ? canaleta : null);
        if (visible) {
            lineasDibujadas = -1; // Forzar redibujado.
            actualizarCanaleta();
        }
    }

    //
    // Canaleta de numeros de linea
    //
    private void actualizarCanaleta() {
        if (contenido.getLeft() == null) {
            return;
        }
        int lineas = contarLineas(editor.getText());
        if (lineas == lineasDibujadas) {
            return;
        }
        lineasDibujadas = lineas;

        // Los numeros se rellenan con espacios a la izquierda para que queden
        // alineados a la derecha: un TextArea no admite -fx-alignment.
        int digitos = digitos(lineas);
        String formato = "%" + digitos + "d";

        StringBuilder sb = new StringBuilder(lineas * (digitos + 1));
        for (int i = 1; i <= lineas; i++) {
            sb.append(String.format(formato, i));
            if (i < lineas) {
                sb.append('\n');
            }
        }
        canaleta.setText(sb.toString());
        ajustarAnchoCanaleta(lineas);
        canaleta.setScrollTop(editor.getScrollTop());
    }

    private static int digitos(int lineas) {
        return Math.max(2, String.valueOf(Math.max(lineas, 1)).length());
    }

    /**
     * Reserva el ancho que ocupan los numeros mas el relleno lateral.
     *
     * <p>El factor 0.62 es el avance de una tipografia monoespaciada respecto
     * a su cuerpo; el sumando cubre el relleno del control y deja holgura para
     * fuentes algo mas anchas que DejaVu Sans Mono.</p>
     */
    private void ajustarAnchoCanaleta(int lineas) {
        double ancho = 26 + digitos(lineas) * (Settings.get().fontSizeProperty().get() * 0.68);
        canaleta.setMinWidth(ancho);
        canaleta.setPrefWidth(ancho);
        canaleta.setMaxWidth(ancho);
    }

    private static int contarLineas(String texto) {
        if (texto == null || texto.isEmpty()) {
            return 1;
        }
        int lineas = 1;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == '\n') {
                lineas++;
            }
        }
        return lineas;
    }

    //
    // Posicion del cursor
    //
    private void actualizarPosicionCursor() {
        int pos = Math.min(editor.getCaretPosition(), editor.getLength());
        String texto = editor.getText();

        int linea = 1;
        int inicioLinea = 0;
        for (int i = 0; i < pos && i < texto.length(); i++) {
            if (texto.charAt(i) == '\n') {
                linea++;
                inicioLinea = i + 1;
            }
        }
        caretLine.set(linea);
        caretColumn.set(pos - inicioLinea + 1);
    }

    /** Coloca el cursor al inicio de la linea indicada (1 es la primera). */
    public void irALinea(int numeroLinea) {
        String texto = editor.getText();
        int objetivo = Math.max(1, numeroLinea);
        int posicion = 0;
        int linea = 1;

        while (linea < objetivo && posicion < texto.length()) {
            int salto = texto.indexOf('\n', posicion);
            if (salto < 0) {
                posicion = texto.length();
                break;
            }
            posicion = salto + 1;
            linea++;
        }

        editor.requestFocus();
        editor.positionCaret(posicion);
        // selectRange asegura que la vista se desplace hasta el cursor.
        editor.selectRange(posicion, posicion);
    }

    //
    // Estado del documento
    //

    /** Marca el contenido actual como el que esta en disco. */
    public void marcarGuardado(Path nuevaRuta, Charset nuevoCharset, long bytes) {
        this.path.set(nuevaRuta);
        this.charset.set(nuevoCharset);
        this.bytesEnDisco = bytes;
        this.contenidoGuardado = editor.getText();
        this.dirty.set(false);
    }

    /** Reemplaza el contenido tras recargar el archivo desde disco. */
    public void recargar(String texto, Charset nuevoCharset, boolean bom, long bytes) {
        this.charset.set(nuevoCharset);
        this.hasBom.set(bom);
        this.bytesEnDisco = bytes;
        this.contenidoGuardado = texto;
        editor.setText(texto);
    }

    //
    // Accesores
    //
    public TextArea getEditor() { return editor; }
    public String getContenido() { return editor.getText(); }

    public ObjectProperty<Path> pathProperty() { return path; }
    public Path getPath() { return path.get(); }

    public ObjectProperty<Charset> charsetProperty() { return charset; }
    public Charset getCharset() { return charset.get(); }
    public void setCharset(Charset c) { charset.set(c); }

    public BooleanProperty dirtyProperty() { return dirty; }
    public boolean isDirty() { return dirty.get(); }

    public BooleanProperty hasBomProperty() { return hasBom; }
    public boolean hasBom() { return hasBom.get(); }
    public void setHasBom(boolean v) { hasBom.set(v); }

    public IntegerProperty caretLineProperty() { return caretLine; }
    public IntegerProperty caretColumnProperty() { return caretColumn; }

    public long getBytesEnDisco() { return bytesEnDisco; }

    /** Nombre visible del documento, sin el asterisco de modificado. */
    public String getNombre() {
        Path p = path.get();
        return p != null ? p.getFileName().toString() : nombreProvisional;
    }

    /** Descripcion del tamano actual del texto en memoria. */
    public String getTamanoTexto() {
        return FileService.formatSize(editor.getText().getBytes(getCharset()).length);
    }

    /** Panel vacio con un mensaje, para cuando no hay documentos abiertos. */
    public static javafx.scene.Node placeholder(String mensaje) {
        Label label = new Label(mensaje);
        label.getStyleClass().add("placeholder");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("placeholder-pane");
        VBox.setVgrow(label, Priority.ALWAYS);
        return box;
    }
}
