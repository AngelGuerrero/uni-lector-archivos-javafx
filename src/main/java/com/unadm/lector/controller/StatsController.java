package com.unadm.lector.controller;

import com.unadm.lector.model.TextStats;
import com.unadm.lector.model.WordCount;
import com.unadm.lector.service.FileService;
import com.unadm.lector.service.TextAnalyzer;
import com.unadm.lector.ui.Dialogs;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla de estadisticas del documento: conteos, promedios y las palabras
 * mas frecuentes, en tabla y en grafica.
 */
public class StatsController {

    /** Cuantas barras se dibujan en la grafica de frecuencias. */
    private static final int BARRAS = 15;

    @FXML private VBox root;
    @FXML private Label lblArchivo;
    @FXML private Label lblCaracteres;
    @FXML private Label lblCaracteresSinEspacios;
    @FXML private Label lblPalabras;
    @FXML private Label lblPalabrasUnicas;
    @FXML private Label lblLineas;
    @FXML private Label lblLineasConTexto;
    @FXML private Label lblParrafos;
    @FXML private Label lblOraciones;
    @FXML private Label lblTamano;
    @FXML private Label lblLineaMasLarga;
    @FXML private Label lblPromedioPalabra;
    @FXML private Label lblPromedioLinea;
    @FXML private Label lblPromedioOracion;
    @FXML private Label lblDiversidad;

    @FXML private CheckBox chkOmitirVacias;
    @FXML private TextField txtFiltro;
    @FXML private TableView<WordCount> tabla;
    @FXML private TableColumn<WordCount, Number> colPosicion;
    @FXML private TableColumn<WordCount, String> colPalabra;
    @FXML private TableColumn<WordCount, Number> colFrecuencia;
    @FXML private TableColumn<WordCount, String> colPorcentaje;
    @FXML private BarChart<String, Number> grafica;
    @FXML private ProgressIndicator indicador;

    private final ObservableList<WordCount> frecuencias = FXCollections.observableArrayList();
    // Locale.of en vez del constructor, que quedo obsoleto a partir de Java 19.
    private final NumberFormat formato = NumberFormat.getIntegerInstance(Locale.of("es", "MX"));

    private String contenido = "";
    private Charset charset = StandardCharsets.UTF_8;
    private String nombreArchivo = "";
    private TextStats ultimas = TextStats.empty();

    @FXML
    private void initialize() {
        configurarTabla();

        // Recalcular al cambiar el filtro de palabras vacias.
        chkOmitirVacias.selectedProperty().addListener((obs, v, n) -> recalcular());
    }

    private void configurarTabla() {
        // Se usan lambdas en lugar de PropertyValueFactory porque WordCount es
        // un record: sus accesores se llaman word(), no getWord().
        colPalabra.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().word()));
        colFrecuencia.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().count()));
        colPorcentaje.setCellValueFactory(c ->
                new SimpleStringProperty(String.format("%.2f %%", c.getValue().percentage())));

        // La posicion es el lugar en la lista mostrada, no un dato del modelo.
        colPosicion.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(0));
        colPosicion.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Number valor, boolean vacio) {
                super.updateItem(valor, vacio);
                setText(vacio || getTableRow() == null ? null : String.valueOf(getIndex() + 1));
            }
        });

        FilteredList<WordCount> filtradas = new FilteredList<>(frecuencias, w -> true);
        txtFiltro.textProperty().addListener((obs, v, n) -> {
            String aguja = n == null ? "" : n.trim().toLowerCase(Locale.ROOT);
            filtradas.setPredicate(w -> aguja.isEmpty() || w.word().contains(aguja));
        });
        tabla.setItems(filtradas);

        tabla.setPlaceholder(new Label("No hay palabras que mostrar."));
    }

    /** Punto de entrada: la ventana principal entrega aqui el texto a analizar. */
    public void analizar(String contenido, Charset charset, String nombreArchivo) {
        this.contenido = contenido == null ? "" : contenido;
        this.charset = charset == null ? StandardCharsets.UTF_8 : charset;
        this.nombreArchivo = nombreArchivo == null ? "" : nombreArchivo;

        lblArchivo.setText(this.nombreArchivo);
        recalcular();
    }

    private void recalcular() {
        indicador.setVisible(true);

        Task<TextStats> tarea = TextAnalyzer.analyzeTask(contenido, charset, chkOmitirVacias.isSelected());
        tarea.setOnSucceeded(e -> {
            ultimas = tarea.getValue();
            pintar(ultimas);
            indicador.setVisible(false);
        });
        tarea.setOnFailed(e -> {
            indicador.setVisible(false);
            Dialogs.error(ventana(), "Error", "No se pudo analizar el texto",
                    FileService.describir(tarea.getException()));
        });

        Thread hilo = new Thread(tarea, "analisis-texto");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void pintar(TextStats s) {
        lblCaracteres.setText(formato.format(s.characters()));
        lblCaracteresSinEspacios.setText(formato.format(s.charactersNoSpaces()));
        lblPalabras.setText(formato.format(s.words()));
        lblPalabrasUnicas.setText(formato.format(s.uniqueWords()));
        lblLineas.setText(formato.format(s.lines()));
        lblLineasConTexto.setText(formato.format(s.nonEmptyLines()));
        lblParrafos.setText(formato.format(s.paragraphs()));
        lblOraciones.setText(formato.format(s.sentences()));
        lblTamano.setText(FileService.formatSize(s.bytes()) + "  (" + charset.displayName() + ")");
        lblLineaMasLarga.setText(formato.format(s.longestLine()) + " caracteres");

        lblPromedioPalabra.setText(String.format("%.2f caracteres", s.averageWordLength()));
        lblPromedioLinea.setText(String.format("%.2f palabras", s.averageWordsPerLine()));
        lblPromedioOracion.setText(String.format("%.2f palabras", s.averageWordsPerSentence()));
        lblDiversidad.setText(String.format("%.1f %%", s.lexicalDiversity() * 100));

        frecuencias.setAll(s.frequencies());
        pintarGrafica(s.frequencies());
    }

    private void pintarGrafica(List<WordCount> datos) {
        grafica.getData().clear();

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName("Apariciones");

        datos.stream().limit(BARRAS)
                .forEach(w -> serie.getData().add(new XYChart.Data<>(w.word(), w.count())));

        grafica.getData().add(serie);
        grafica.setLegendVisible(false);
        grafica.setAnimated(false);
    }

    //
    // Acciones
    //
    @FXML
    private void exportar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar estadisticas");
        chooser.setInitialFileName(nombreBase() + "-estadisticas.csv");
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("CSV", "*.csv"),
                new FileChooser.ExtensionFilter("Texto", "*.txt"));

        File destino = chooser.showSaveDialog(ventana());
        if (destino == null) {
            return;
        }

        try {
            Files.writeString(destino.toPath(), construirCsv(), StandardCharsets.UTF_8);
            Dialogs.info(ventana(), "Exportar", "Estadisticas exportadas",
                    "Se guardaron en:\n" + destino.getAbsolutePath());
        } catch (Exception e) {
            Dialogs.error(ventana(), "Error", "No se pudo exportar el archivo", e);
        }
    }

    private String construirCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Metrica;Valor\n");
        sb.append("Archivo;").append(csv(nombreArchivo)).append('\n');
        sb.append("Caracteres;").append(ultimas.characters()).append('\n');
        sb.append("Caracteres sin espacios;").append(ultimas.charactersNoSpaces()).append('\n');
        sb.append("Palabras;").append(ultimas.words()).append('\n');
        sb.append("Palabras distintas;").append(ultimas.uniqueWords()).append('\n');
        sb.append("Lineas;").append(ultimas.lines()).append('\n');
        sb.append("Lineas con texto;").append(ultimas.nonEmptyLines()).append('\n');
        sb.append("Parrafos;").append(ultimas.paragraphs()).append('\n');
        sb.append("Oraciones;").append(ultimas.sentences()).append('\n');
        sb.append("Tamano en bytes;").append(ultimas.bytes()).append('\n');
        sb.append("Linea mas larga;").append(ultimas.longestLine()).append('\n');
        sb.append(String.format("Longitud media de palabra;%.4f%n", ultimas.averageWordLength()));
        sb.append(String.format("Palabras por linea;%.4f%n", ultimas.averageWordsPerLine()));
        sb.append(String.format("Palabras por oracion;%.4f%n", ultimas.averageWordsPerSentence()));
        sb.append(String.format("Diversidad lexica;%.4f%n", ultimas.lexicalDiversity()));

        sb.append("\nPosicion;Palabra;Apariciones;Porcentaje\n");
        List<WordCount> lista = ultimas.frequencies();
        for (int i = 0; i < lista.size(); i++) {
            WordCount w = lista.get(i);
            sb.append(i + 1).append(';')
                    .append(csv(w.word())).append(';')
                    .append(w.count()).append(';')
                    .append(String.format("%.4f", w.percentage())).append('\n');
        }
        return sb.toString();
    }

    /** Escapa un campo para CSV separado por punto y coma. */
    private static String csv(String valor) {
        if (valor == null) {
            return "";
        }
        if (valor.contains(";") || valor.contains("\"") || valor.contains("\n")) {
            return '"' + valor.replace("\"", "\"\"") + '"';
        }
        return valor;
    }

    private String nombreBase() {
        Path p = Path.of(nombreArchivo.isBlank() ? "documento" : nombreArchivo);
        String nombre = p.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        return punto > 0 ? nombre.substring(0, punto) : nombre;
    }

    @FXML
    private void cerrar() {
        if (ventana() != null) {
            ventana().hide();
        }
    }

    private javafx.stage.Window ventana() {
        return root == null || root.getScene() == null ? null : root.getScene().getWindow();
    }
}
