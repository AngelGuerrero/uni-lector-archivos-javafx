package com.unadm.lector.controller;

import com.unadm.lector.App;
import com.unadm.lector.service.FileService;
import com.unadm.lector.service.Settings;
import com.unadm.lector.ui.Dialogs;
import com.unadm.lector.ui.DocumentTab;
import com.unadm.lector.ui.Windows;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controlador de la ventana principal.
 *
 * <p>Coordina las pestanas de documentos, el menu, la barra de herramientas y
 * la barra de estado, y delega el trabajo pesado en los servicios.</p>
 */
public class MainController {

    @FXML private BorderPane root;
    @FXML private TabPane tabPane;

    @FXML private Menu menuRecientes;
    @FXML private MenuItem miGuardar;
    @FXML private MenuItem miGuardarComo;
    @FXML private MenuItem miRecargar;
    @FXML private MenuItem miCerrarPestana;
    @FXML private MenuItem miDeshacer;
    @FXML private MenuItem miRehacer;
    @FXML private MenuItem miCortar;
    @FXML private MenuItem miCopiar;
    @FXML private MenuItem miPegar;
    @FXML private MenuItem miSeleccionarTodo;
    @FXML private MenuItem miBuscar;
    @FXML private MenuItem miIrALinea;
    @FXML private MenuItem miEstadisticas;
    @FXML private CheckMenuItem miAjusteLinea;
    @FXML private CheckMenuItem miNumerosLinea;
    @FXML private CheckMenuItem miTemaOscuro;

    @FXML private Button btnGuardar;
    @FXML private Button btnBuscar;
    @FXML private Button btnEstadisticas;

    @FXML private Label lblEstado;
    @FXML private Label lblPosicion;
    @FXML private Label lblConteo;
    @FXML private Label lblTamano;
    @FXML private ChoiceBox<Charset> cbCodificacion;
    @FXML private ProgressBar barraProgreso;

    /** Hilos para la E/S: demonios, para que no impidan cerrar el programa. */
    private final ExecutorService ejecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "lector-io");
        t.setDaemon(true);
        return t;
    });

    /** La ventana de Buscar y reemplazar es unica y no modal. */
    private Stage ventanaBuscar;

    /** Evita que cambiar la codificacion en la barra de estado dispare una recarga en cadena. */
    private boolean actualizandoCodificacion = false;

    @FXML
    private void initialize() {
        configurarTabPane();
        configurarMenus();
        configurarBarraEstado();
        configurarArrastrarSoltar();
        reconstruirRecientes();

        Settings.get().getRecentFiles().addListener(
                (javafx.collections.ListChangeListener<String>) c -> reconstruirRecientes());

        // Sin documentos abiertos se muestra una invitacion a abrir uno.
        tabPane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) c -> actualizarEstadoVacio());
        actualizarEstadoVacio();
    }

    /** Pantalla de bienvenida que ocupa el centro mientras no hay pestanas. */
    private final javafx.scene.Node bienvenida = DocumentTab.placeholder(
            "No hay ningun documento abierto.\n\n"
            + "Abre uno con Ctrl+O, crea uno nuevo con Ctrl+N,\n"
            + "o arrastra archivos hasta esta ventana.");

    //
    // Configuracion inicial
    //
    private void configurarTabPane() {
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.getSelectionModel().selectedItemProperty().addListener(
                (obs, anterior, actual) -> alCambiarPestana(anterior, actual));
    }

    private void configurarMenus() {
        Settings s = Settings.get();

        // Las opciones de "Ver" reflejan y modifican las preferencias globales.
        miAjusteLinea.setSelected(s.wrapTextProperty().get());
        miAjusteLinea.selectedProperty().addListener((obs, v, n) -> {
            s.wrapTextProperty().set(n);
            documentos().forEach(d -> d.setWrapText(n));
        });

        miNumerosLinea.setSelected(s.showLineNumbersProperty().get());
        miNumerosLinea.selectedProperty().addListener((obs, v, n) -> s.showLineNumbersProperty().set(n));

        miTemaOscuro.setSelected(s.getTheme() == Settings.Theme.OSCURO);
        miTemaOscuro.selectedProperty().addListener((obs, v, n) -> {
            s.setTheme(n ? Settings.Theme.OSCURO : Settings.Theme.CLARO);
            Windows.refreshTheme();
        });

        // Todo lo que opera sobre un documento se desactiva cuando no hay ninguno.
        var sinDocumento = Bindings.createBooleanBinding(
                () -> documentoActivo().isEmpty(),
                tabPane.getSelectionModel().selectedItemProperty(), tabPane.getTabs());

        List<MenuItem> dependientes = List.of(
                miGuardar, miGuardarComo, miRecargar, miCerrarPestana, miDeshacer, miRehacer,
                miCortar, miCopiar, miPegar, miSeleccionarTodo, miBuscar, miIrALinea, miEstadisticas);
        dependientes.forEach(mi -> mi.disableProperty().bind(sinDocumento));

        btnGuardar.disableProperty().bind(sinDocumento);
        btnBuscar.disableProperty().bind(sinDocumento);
        btnEstadisticas.disableProperty().bind(sinDocumento);
        cbCodificacion.disableProperty().bind(sinDocumento);
    }

    private void configurarBarraEstado() {
        cbCodificacion.getItems().setAll(Settings.CODIFICACIONES);
        cbCodificacion.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Charset c) { return c == null ? "" : c.displayName(); }
            @Override public Charset fromString(String s) { return Settings.charsetOrUtf8(s); }
        });

        cbCodificacion.getSelectionModel().selectedItemProperty().addListener((obs, v, n) -> {
            if (actualizandoCodificacion || n == null) {
                return;
            }
            documentoActivo().ifPresent(doc -> cambiarCodificacion(doc, n));
        });

        barraProgreso.setVisible(false);
        barraProgreso.setManaged(false);
    }

    private void configurarArrastrarSoltar() {
        root.setOnDragOver(this::alArrastrarSobre);
        root.setOnDragDropped(this::alSoltar);
    }

    //
    // Ciclo de vida
    //

    /** Abre los archivos pasados por linea de comandos al iniciar. */
    public void abrirDesdeArgumentos(List<Path> rutas) {
        if (rutas.isEmpty()) {
            return;
        }
        rutas.forEach(p -> abrirArchivo(p, null));
    }

    /** Se invoca al intentar cerrar la ventana: pide confirmar los cambios pendientes. */
    public void onCloseRequest(WindowEvent event) {
        if (!confirmarCierreDeTodo()) {
            event.consume();
            return;
        }
        ejecutor.shutdownNow();
        Settings.get().save();
    }

    //
    // Acciones del menu Archivo
    //
    @FXML
    private void nuevoDocumento() {
        DocumentTab tab = new DocumentTab();
        agregarPestana(tab);
        estado("Documento nuevo creado.", Nivel.INFO);
    }

    /**
     * Anade la pestana y le pone el guardian de cierre.
     *
     * <p>Sin esto, la X de la pestana la cierra de inmediato y los cambios sin
     * guardar se pierden sin aviso, a diferencia de la opcion del menu.</p>
     */
    private void agregarPestana(DocumentTab tab) {
        tab.setOnCloseRequest(event -> {
            if (!confirmarCierre(tab)) {
                event.consume();
            }
        });
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    @FXML
    private void seleccionarArchivo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar archivo de texto");
        chooser.setInitialDirectory(directorioInicial());
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("Archivos de texto", "*.txt", "*.log", "*.md", "*.csv"),
                new FileChooser.ExtensionFilter("Codigo fuente", "*.java", "*.xml", "*.json", "*.css",
                        "*.js", "*.html", "*.yml", "*.yaml", "*.sql", "*.py", "*.sh"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));

        List<File> seleccion = chooser.showOpenMultipleDialog(ventana());
        if (seleccion == null || seleccion.isEmpty()) {
            estado("No se selecciono ningun archivo.", Nivel.INFO);
            return;
        }

        recordarDirectorio(seleccion.get(0).toPath());
        seleccion.forEach(f -> abrirArchivo(f.toPath(), null));
    }

    @FXML
    private void guardar() {
        documentoActivo().ifPresent(doc -> {
            if (doc.getPath() == null) {
                guardarComo();
            } else {
                escribir(doc, doc.getPath());
            }
        });
    }

    @FXML
    private void guardarComo() {
        documentoActivo().ifPresent(doc -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar como");
            chooser.setInitialDirectory(doc.getPath() != null
                    ? doc.getPath().toAbsolutePath().getParent().toFile()
                    : directorioInicial());
            chooser.setInitialFileName(doc.getPath() != null
                    ? doc.getPath().getFileName().toString()
                    : doc.getNombre() + ".txt");
            chooser.getExtensionFilters().setAll(
                    new FileChooser.ExtensionFilter("Archivos de texto", "*.txt"),
                    new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));

            File destino = chooser.showSaveDialog(ventana());
            if (destino == null) {
                estado("Guardado cancelado.", Nivel.INFO);
                return;
            }
            recordarDirectorio(destino.toPath());
            escribir(doc, destino.toPath());
        });
    }

    @FXML
    private void recargar() {
        documentoActivo().ifPresent(doc -> {
            if (doc.getPath() == null) {
                estado("El documento aun no se ha guardado en disco.", Nivel.INFO);
                return;
            }
            if (doc.isDirty() && !Dialogs.confirm(ventana(), "Recargar",
                    "El documento tiene cambios sin guardar.",
                    "Al recargar desde el disco se perderan. Continuar?")) {
                return;
            }

            Task<FileService.LoadedFile> tarea = FileService.loadTask(doc.getPath(), doc.getCharset());
            enlazarProgreso(tarea);
            tarea.setOnSucceeded(e -> {
                FileService.LoadedFile f = tarea.getValue();
                doc.recargar(f.content(), f.charset(), f.hadBom(), f.bytes());
                actualizarBarraEstado();
                estado("Recargado desde disco: " + f.path().getFileName(), Nivel.EXITO);
            });
            tarea.setOnFailed(e -> fallo("No se pudo recargar el archivo", tarea.getException()));
            ejecutor.submit(tarea);
        });
    }

    @FXML
    private void cerrarPestana() {
        documentoActivo().ifPresent(doc -> {
            if (confirmarCierre(doc)) {
                doc.liberar();
                tabPane.getTabs().remove(doc);
            }
        });
    }

    @FXML
    private void cerrarApp() {
        Window w = ventana();
        if (w != null) {
            // Se dispara la misma ruta que al pulsar la X, para no saltarse
            // la confirmacion de cambios sin guardar.
            w.fireEvent(new WindowEvent(w, WindowEvent.WINDOW_CLOSE_REQUEST));
        }
    }

    //
    // Acciones del menu Editar
    //
    @FXML private void deshacer() { conEditor(TextArea::undo); }
    @FXML private void rehacer() { conEditor(TextArea::redo); }
    @FXML private void cortar() { conEditor(TextArea::cut); }
    @FXML private void copiar() { conEditor(TextArea::copy); }
    @FXML private void pegar() { conEditor(TextArea::paste); }
    @FXML private void seleccionarTodo() { conEditor(TextArea::selectAll); }

    @FXML
    private void copiarRuta() {
        documentoActivo().ifPresent(doc -> {
            if (doc.getPath() == null) {
                estado("El documento aun no tiene ruta en disco.", Nivel.INFO);
                return;
            }
            ClipboardContent contenido = new ClipboardContent();
            contenido.putString(doc.getPath().toAbsolutePath().toString());
            Clipboard.getSystemClipboard().setContent(contenido);
            estado("Ruta copiada al portapapeles.", Nivel.EXITO);
        });
    }

    @FXML
    private void buscarYReemplazar() {
        if (ventanaBuscar != null && ventanaBuscar.isShowing()) {
            ventanaBuscar.toFront();
            ventanaBuscar.requestFocus();
            return;
        }
        ventanaBuscar = Windows.<FindReplaceController>open(
                ventana(), "FindReplaceView.fxml", "Buscar y reemplazar", false,
                c -> c.enlazar(this));
    }

    @FXML
    private void irALinea() {
        documentoActivo().ifPresent(doc -> {
            Optional<String> respuesta = Dialogs.prompt(ventana(), "Ir a linea",
                    "Numero de linea", "Linea:", String.valueOf(doc.caretLineProperty().get()));

            respuesta.ifPresent(texto -> {
                try {
                    doc.irALinea(Integer.parseInt(texto.trim()));
                } catch (NumberFormatException e) {
                    Dialogs.warn(ventana(), "Ir a linea", "Valor no valido",
                            "\"" + texto + "\" no es un numero de linea.");
                }
            });
        });
    }

    //
    // Acciones del menu Ver y Herramientas
    //
    @FXML
    private void aumentarFuente() {
        ajustarFuente(+1);
    }

    @FXML
    private void disminuirFuente() {
        ajustarFuente(-1);
    }

    @FXML
    private void restablecerFuente() {
        Settings.get().fontSizeProperty().set(14);
        estado("Tamano de fuente restablecido a 14.", Nivel.INFO);
    }

    private void ajustarFuente(int delta) {
        var prop = Settings.get().fontSizeProperty();
        int nuevo = Math.clamp(prop.get() + delta, Settings.TAMANO_FUENTE_MIN, Settings.TAMANO_FUENTE_MAX);
        prop.set(nuevo);
        estado("Tamano de fuente: " + nuevo + " px", Nivel.INFO);
    }

    @FXML
    private void mostrarEstadisticas() {
        documentoActivo().ifPresent(doc -> Windows.<StatsController>open(
                ventana(), "StatsView.fxml", "Estadisticas - " + doc.getNombre(), true,
                c -> c.analizar(doc.getContenido(), doc.getCharset(), doc.getNombre())));
    }

    @FXML
    private void mostrarPreferencias() {
        Windows.<PreferencesController>open(
                ventana(), "PreferencesView.fxml", "Preferencias", true,
                c -> c.enlazar(this));
    }

    @FXML
    private void mostrarInformacion() {
        Windows.open(ventana(), "AboutView.fxml", "Acerca de " + App.NOMBRE, true, null);
    }

    //
    // Apertura y guardado
    //

    /** Abre un archivo en una pestana nueva, o activa la que ya lo tiene. */
    public void abrirArchivo(Path ruta, Charset forzada) {
        Path normalizada = ruta.toAbsolutePath().normalize();

        Optional<DocumentTab> yaAbierto = documentos().stream()
                .filter(d -> d.getPath() != null && d.getPath().equals(normalizada))
                .findFirst();

        if (yaAbierto.isPresent() && forzada == null) {
            tabPane.getSelectionModel().select(yaAbierto.get());
            estado("El archivo ya estaba abierto.", Nivel.INFO);
            return;
        }

        Task<FileService.LoadedFile> tarea = FileService.loadTask(normalizada, forzada);
        enlazarProgreso(tarea);

        tarea.setOnSucceeded(e -> {
            FileService.LoadedFile f = tarea.getValue();
            if (f == null) {
                return; // Tarea cancelada.
            }

            DocumentTab destino = yaAbierto.orElse(null);
            if (destino != null) {
                destino.recargar(f.content(), f.charset(), f.hadBom(), f.bytes());
                tabPane.getSelectionModel().select(destino);
            } else {
                destino = new DocumentTab(f.path(), f.content(), f.charset(), f.hadBom(), f.bytes());
                agregarPestana(destino);
            }

            Settings.get().addRecentFile(f.path());
            recordarDirectorio(f.path());
            actualizarBarraEstado();

            estado("Archivo cargado: " + f.path().getFileName()
                    + "  (" + FileService.formatSize(f.bytes()) + ", " + f.charset().displayName() + ")",
                    Nivel.EXITO);
        });

        tarea.setOnFailed(e -> {
            // Un archivo que ya no existe deja de ser un "reciente" util.
            Settings.get().removeRecentFile(normalizada.toString());
            fallo("No se pudo leer el archivo", tarea.getException());
        });

        ejecutor.submit(tarea);
    }

    private void escribir(DocumentTab doc, Path destino) {
        Task<Path> tarea = FileService.saveTask(destino, doc.getContenido(), doc.getCharset(), doc.hasBom());
        enlazarProgreso(tarea);

        tarea.setOnSucceeded(e -> {
            long bytes = 0;
            try {
                bytes = Files.size(destino);
            } catch (Exception ignored) {
                // El tamano es informativo; si falla se deja en cero.
            }
            doc.marcarGuardado(destino, doc.getCharset(), bytes);
            Settings.get().addRecentFile(destino);
            actualizarBarraEstado();
            estado("Guardado: " + destino.getFileName() + "  (" + FileService.formatSize(bytes) + ")",
                    Nivel.EXITO);
        });

        tarea.setOnFailed(e -> fallo("No se pudo guardar el archivo", tarea.getException()));
        ejecutor.submit(tarea);
    }

    private void cambiarCodificacion(DocumentTab doc, Charset nueva) {
        if (nueva.equals(doc.getCharset())) {
            return;
        }
        if (doc.getPath() == null) {
            // Documento nuevo: basta con cambiar como se escribira.
            doc.setCharset(nueva);
            estado("El documento se guardara en " + nueva.displayName() + ".", Nivel.INFO);
            return;
        }
        if (doc.isDirty()) {
            boolean seguir = Dialogs.confirm(ventana(), "Cambiar codificacion",
                    "El documento tiene cambios sin guardar.",
                    "Releer el archivo en " + nueva.displayName() + " descartara esos cambios. Continuar?");
            if (!seguir) {
                actualizarBarraEstado(); // Devuelve el selector a su valor real.
                return;
            }
        }
        // Se relee el archivo interpretando los bytes con la nueva codificacion.
        abrirArchivo(doc.getPath(), nueva);
    }

    //
    // Arrastrar y soltar
    //
    private void alArrastrarSobre(DragEvent event) {
        if (event.getGestureSource() == null && event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void alSoltar(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean aceptado = false;

        if (db.hasFiles()) {
            List<File> archivos = db.getFiles().stream().filter(File::isFile).toList();
            archivos.forEach(f -> abrirArchivo(f.toPath(), null));
            aceptado = !archivos.isEmpty();
            if (!aceptado) {
                estado("Solo se pueden soltar archivos, no carpetas.", Nivel.ERROR);
            }
        }

        event.setDropCompleted(aceptado);
        event.consume();
    }

    //
    // Archivos recientes
    //
    private void reconstruirRecientes() {
        menuRecientes.getItems().clear();

        List<String> recientes = Settings.get().getRecentFiles();
        if (recientes.isEmpty()) {
            MenuItem vacio = new MenuItem("(sin archivos recientes)");
            vacio.setDisable(true);
            menuRecientes.getItems().add(vacio);
            return;
        }

        for (String ruta : List.copyOf(recientes)) {
            Path p = Path.of(ruta);
            MenuItem item = new MenuItem(p.getFileName() + "   —   " + p.getParent());
            item.setOnAction(e -> abrirArchivo(p, null));
            menuRecientes.getItems().add(item);
        }

        menuRecientes.getItems().add(new SeparatorMenuItem());
        MenuItem limpiar = new MenuItem("Limpiar la lista");
        limpiar.setOnAction(e -> Settings.get().clearRecentFiles());
        menuRecientes.getItems().add(limpiar);
    }

    //
    // Barra de estado
    //
    private void alCambiarPestana(Tab anterior, Tab actual) {
        if (anterior instanceof DocumentTab viejo) {
            lblPosicion.textProperty().unbind();
            viejo.getEditor().textProperty().removeListener(alCambiarTexto);
        }
        if (actual instanceof DocumentTab nuevo) {
            lblPosicion.textProperty().bind(Bindings.format("Lin %d, Col %d",
                    nuevo.caretLineProperty(), nuevo.caretColumnProperty()));
            nuevo.getEditor().textProperty().addListener(alCambiarTexto);
            nuevo.aplicarPreferencias();
        } else {
            lblPosicion.setText("");
        }
        actualizarBarraEstado();
    }

    private final javafx.beans.value.ChangeListener<String> alCambiarTexto =
            (obs, viejo, nuevo) -> actualizarConteoRapido(nuevo);

    private void actualizarBarraEstado() {
        Optional<DocumentTab> doc = documentoActivo();
        if (doc.isEmpty()) {
            lblConteo.setText("");
            lblTamano.setText("");
            lblPosicion.setText("");
            return;
        }

        DocumentTab d = doc.get();
        actualizarConteoRapido(d.getContenido());

        actualizandoCodificacion = true;
        cbCodificacion.getSelectionModel().select(d.getCharset());
        actualizandoCodificacion = false;
    }

    /**
     * Conteo ligero para la barra de estado: recorre el texto una sola vez.
     * El analisis completo vive en la pantalla de estadisticas.
     *
     * <p>Una palabra es una secuencia de letras o digitos, admitiendo guion y
     * apostrofo internos. Es la misma definicion que usa
     * {@link com.unadm.lector.service.TextAnalyzer}: si aqui se contaran los
     * bloques separados por espacios, una linea como "======" sumaria una
     * palabra y las dos pantallas darian cifras distintas del mismo documento.</p>
     */
    private void actualizarConteoRapido(String texto) {
        if (texto == null) {
            texto = "";
        }
        int caracteres = texto.length();
        int lineas = 1;
        int palabras = 0;
        boolean dentroDePalabra = false;

        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '\n') {
                lineas++;
            }

            boolean formaPalabra = Character.isLetterOrDigit(c) || c == '-' || c == '\'' || c == '’';
            if (!formaPalabra) {
                dentroDePalabra = false;
            } else if (Character.isLetterOrDigit(c) && !dentroDePalabra) {
                dentroDePalabra = true;
                palabras++;
            }
        }

        lblConteo.setText(String.format("%,d caracteres   %,d palabras   %,d lineas",
                caracteres, palabras, lineas));
        documentoActivo().ifPresent(d -> lblTamano.setText(d.getTamanoTexto()));
    }

    private void actualizarEstadoVacio() {
        boolean vacio = tabPane.getTabs().isEmpty();
        // El TabPane se retira del centro cuando no tiene pestanas: un TabPane
        // vacio solo deja un rectangulo gris sin explicacion.
        root.setCenter(vacio ? bienvenida : tabPane);

        if (vacio) {
            estado("Listo. Abre un archivo con Ctrl+O, o arrastralo hasta esta ventana.", Nivel.NEUTRO);
            lblConteo.setText("");
            lblTamano.setText("");
            lblPosicion.setText("");
        }
    }

    //
    // Progreso y mensajes
    //
    /**
     * Muestra el avance de una tarea en la barra inferior.
     *
     * <p>El mensaje se copia con una escucha en vez de enlazarse: si se enlazara,
     * el texto quedaria de solo lectura y el mensaje final de exito, que llega
     * despues, no podria escribirse.</p>
     */
    private void enlazarProgreso(Task<?> tarea) {
        barraProgreso.setVisible(true);
        barraProgreso.setManaged(true);
        barraProgreso.progressProperty().bind(tarea.progressProperty());

        tarea.messageProperty().addListener((obs, viejo, nuevo) -> estado(nuevo, Nivel.NEUTRO));

        tarea.runningProperty().addListener((obs, corriendo, sigue) -> {
            if (!sigue) {
                barraProgreso.progressProperty().unbind();
                barraProgreso.setProgress(0);
                barraProgreso.setVisible(false);
                barraProgreso.setManaged(false);
            }
        });
    }

    private void fallo(String cabecera, Throwable error) {
        String detalle = FileService.describir(error);
        estado(detalle, Nivel.ERROR);
        Dialogs.error(ventana(), "Error", cabecera, detalle);
    }

    /** Nivel del mensaje de la barra de estado; determina el color. */
    private enum Nivel { NEUTRO, INFO, EXITO, ERROR }

    private void estado(String mensaje, Nivel nivel) {
        lblEstado.setText(mensaje == null ? "" : mensaje);
        lblEstado.getStyleClass().removeAll("estado-error", "estado-exito", "estado-info");
        switch (nivel) {
            case ERROR -> lblEstado.getStyleClass().add("estado-error");
            case EXITO -> lblEstado.getStyleClass().add("estado-exito");
            case INFO -> lblEstado.getStyleClass().add("estado-info");
            case NEUTRO -> { /* sin clase adicional */ }
        }
    }

    //
    // Utilidades
    //

    /** Documentos abiertos, en el orden de las pestanas. */
    public List<DocumentTab> documentos() {
        List<DocumentTab> lista = new ArrayList<>();
        for (Tab t : tabPane.getTabs()) {
            if (t instanceof DocumentTab d) {
                lista.add(d);
            }
        }
        return lista;
    }

    /** El documento de la pestana activa, si hay alguna. */
    public Optional<DocumentTab> documentoActivo() {
        Tab t = tabPane == null ? null : tabPane.getSelectionModel().getSelectedItem();
        return t instanceof DocumentTab d ? Optional.of(d) : Optional.empty();
    }

    /** Aplica a todos los documentos los cambios hechos en Preferencias. */
    public void refrescarDocumentos() {
        documentos().forEach(DocumentTab::aplicarPreferencias);
        miAjusteLinea.setSelected(Settings.get().wrapTextProperty().get());
        miNumerosLinea.setSelected(Settings.get().showLineNumbersProperty().get());
        miTemaOscuro.setSelected(Settings.get().getTheme() == Settings.Theme.OSCURO);
    }

    public Window ventana() {
        return root == null || root.getScene() == null ? null : root.getScene().getWindow();
    }

    private void conEditor(java.util.function.Consumer<TextArea> accion) {
        documentoActivo().ifPresent(d -> accion.accept(d.getEditor()));
    }

    private File directorioInicial() {
        String ultimo = Settings.get().lastDirectoryProperty().get();
        if (ultimo != null && !ultimo.isBlank() && Files.isDirectory(Path.of(ultimo))) {
            return new File(ultimo);
        }
        // En el contenedor, /data es el volumen que el usuario monta desde su
        // equipo. En Windows esa ruta se resolveria como C:\data, que no tiene
        // nada que ver, asi que solo se consulta fuera de Windows.
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows")) {
            Path datos = Path.of("/data");
            if (Files.isDirectory(datos)) {
                return datos.toFile();
            }
        }
        return new File(System.getProperty("user.home"));
    }

    private void recordarDirectorio(Path archivo) {
        Path padre = archivo.toAbsolutePath().getParent();
        if (padre != null) {
            Settings.get().lastDirectoryProperty().set(padre.toString());
        }
    }

    //
    // Confirmaciones de cierre
    //
    private boolean confirmarCierre(DocumentTab doc) {
        if (!doc.isDirty()) {
            return true;
        }
        tabPane.getSelectionModel().select(doc);

        return switch (Dialogs.askSave(ventana(), doc.getNombre())) {
            case DESCARTAR -> true;
            case CANCELAR -> false;
            case GUARDAR -> guardarSincrono(doc);
        };
    }

    private boolean confirmarCierreDeTodo() {
        for (DocumentTab doc : documentos()) {
            if (!confirmarCierre(doc)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Guarda de forma bloqueante. Se usa solo al cerrar, donde no se puede
     * dejar la escritura en segundo plano porque la aplicacion esta por morir.
     */
    private boolean guardarSincrono(DocumentTab doc) {
        Path destino = doc.getPath();
        if (destino == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar " + doc.getNombre());
            chooser.setInitialDirectory(directorioInicial());
            chooser.setInitialFileName(doc.getNombre() + ".txt");
            File elegido = chooser.showSaveDialog(ventana());
            if (elegido == null) {
                return false; // Cancelar el guardado cancela el cierre.
            }
            destino = elegido.toPath();
        }

        try {
            Task<Path> tarea = FileService.saveTask(destino, doc.getContenido(), doc.getCharset(), doc.hasBom());
            tarea.run();
            if (tarea.getException() != null) {
                throw tarea.getException();
            }
            doc.marcarGuardado(destino, doc.getCharset(), Files.size(destino));
            return true;
        } catch (Throwable e) {
            Dialogs.error(ventana(), "Error", "No se pudo guardar \"" + doc.getNombre() + "\"",
                    FileService.describir(e));
            return false;
        }
    }

    /** Permite a la ventana de busqueda escribir en la barra de estado. */
    public void mensaje(String texto, boolean error) {
        estado(texto, error ? Nivel.ERROR : Nivel.INFO);
    }
}
