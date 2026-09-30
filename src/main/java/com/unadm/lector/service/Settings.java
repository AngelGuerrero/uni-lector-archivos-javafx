package com.unadm.lector.service;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

/**
 * Preferencias del usuario, persistidas en un archivo {@code .properties}.
 *
 * <p>Todas las opciones son propiedades de JavaFX, as&iacute; que las pantallas
 * pueden enlazarse a ellas y reaccionar en vivo a los cambios.</p>
 */
public final class Settings {

    /** Temas disponibles para la interfaz. */
    public enum Theme {
        CLARO("Claro", "css/light.css"),
        OSCURO("Oscuro", "css/dark.css");

        private final String etiqueta;
        private final String hoja;

        Theme(String etiqueta, String hoja) {
            this.etiqueta = etiqueta;
            this.hoja = hoja;
        }

        public String hoja() {
            return hoja;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    /** Codificaciones que ofrece el programa para leer y escribir. */
    public static final List<Charset> CODIFICACIONES = codificacionesDisponibles();

    /**
     * Filtra las codificaciones que este runtime sabe manejar.
     *
     * <p>Solo UTF-8, UTF-16, ISO-8859-1 y US-ASCII estan garantizadas en
     * {@code java.base}. Las demas, como windows-1252, viven en el modulo
     * {@code jdk.charsets}, que una imagen recortada con jlink puede no
     * incluir: pedirlas directamente tumbaria el programa al arrancar.</p>
     */
    private static List<Charset> codificacionesDisponibles() {
        List<Charset> disponibles = new ArrayList<>();

        disponibles.add(StandardCharsets.UTF_8);
        disponibles.add(StandardCharsets.ISO_8859_1);
        for (String opcional : List.of("windows-1252")) {
            try {
                disponibles.add(Charset.forName(opcional));
            } catch (RuntimeException e) {
                System.err.println("Codificacion no disponible en este runtime: " + opcional);
            }
        }
        disponibles.add(StandardCharsets.UTF_16LE);
        disponibles.add(StandardCharsets.UTF_16BE);
        disponibles.add(StandardCharsets.US_ASCII);

        return List.copyOf(disponibles);
    }

    /**
     * Tipograf&iacute;as monoespaciadas preferidas, en orden de preferencia.
     *
     * <p>La lista mezcla familias de Windows y de Linux a propósito: el programa
     * corre en los dos sitios y elige la primera que exista de verdad. Consolas
     * viene con Windows desde Vista y DejaVu Sans Mono es la habitual en Linux.</p>
     */
    public static final List<String> TIPOGRAFIAS = List.of(
            "Consolas", "JetBrains Mono", "Fira Code",
            "DejaVu Sans Mono", "Liberation Mono", "Noto Sans Mono", "Ubuntu Mono",
            "Courier New", "Monospaced"
    );

    /*
     * Cascadia Mono se deja fuera a proposito: viene con Windows Terminal y
     * seria una buena candidata, pero es una fuente variable y JavaFX 21 la
     * carga con la tabla de glifos descolocada, de modo que el texto sale
     * convertido en simbolos ilegibles.
     */

    /**
     * Primera tipograf&iacute;a de {@link #TIPOGRAFIAS} instalada en el sistema.
     *
     * <p>Fijar una concreta en el c&oacute;digo no sirve: "DejaVu Sans Mono" no
     * existe en Windows y "Consolas" no existe en Linux, y cuando JavaFX no
     * encuentra la familia pedida cae en una fuente proporcional, con lo que el
     * editor deja de estar alineado.</p>
     *
     * <p>Solo debe llamarse con el motor gr&aacute;fico ya arrancado.</p>
     */
    public static String tipografiaPorOmision() {
        try {
            List<String> instaladas = javafx.scene.text.Font.getFamilies();
            for (String preferida : TIPOGRAFIAS) {
                if (instaladas.contains(preferida)) {
                    return preferida;
                }
            }
        } catch (RuntimeException e) {
            // Sin motor grafico todavia: se usa la familia logica.
        }
        // "Monospaced" es un alias que la JVM siempre resuelve a algo monoespaciado.
        return "Monospaced";
    }

    public static final int TAMANO_FUENTE_MIN = 9;
    public static final int TAMANO_FUENTE_MAX = 40;

    private static final Settings INSTANCIA = new Settings();

    private final StringProperty theme = new SimpleStringProperty(Theme.CLARO.name());
    // Se deja vacia a proposito: la familia real se resuelve en load(), cuando
    // el motor grafico ya puede decir que tipografias hay instaladas.
    private final StringProperty fontFamily = new SimpleStringProperty("");
    private final IntegerProperty fontSize = new SimpleIntegerProperty(14);
    private final StringProperty defaultCharset = new SimpleStringProperty(StandardCharsets.UTF_8.name());
    private final BooleanProperty wrapText = new SimpleBooleanProperty(false);
    private final BooleanProperty showLineNumbers = new SimpleBooleanProperty(true);
    private final BooleanProperty detectCharset = new SimpleBooleanProperty(true);
    private final IntegerProperty maxRecentFiles = new SimpleIntegerProperty(10);
    private final StringProperty lastDirectory = new SimpleStringProperty("");

    private final ObservableList<String> recentFiles = FXCollections.observableArrayList();

    private Settings() {
        // Singleton.
    }

    public static Settings get() {
        return INSTANCIA;
    }

    //
    // Propiedades
    //
    public StringProperty themeProperty() { return theme; }
    public StringProperty fontFamilyProperty() { return fontFamily; }
    public IntegerProperty fontSizeProperty() { return fontSize; }
    public StringProperty defaultCharsetProperty() { return defaultCharset; }
    public BooleanProperty wrapTextProperty() { return wrapText; }
    public BooleanProperty showLineNumbersProperty() { return showLineNumbers; }
    public BooleanProperty detectCharsetProperty() { return detectCharset; }
    public IntegerProperty maxRecentFilesProperty() { return maxRecentFiles; }
    public StringProperty lastDirectoryProperty() { return lastDirectory; }
    public ObservableList<String> getRecentFiles() { return recentFiles; }

    public Theme getTheme() {
        try {
            return Theme.valueOf(theme.get());
        } catch (IllegalArgumentException e) {
            return Theme.CLARO;
        }
    }

    public void setTheme(Theme value) {
        theme.set(value.name());
    }

    public Charset getDefaultCharset() {
        return charsetOrUtf8(defaultCharset.get());
    }

    public void setDefaultCharset(Charset value) {
        defaultCharset.set(value.name());
    }

    /** Devuelve la codificaci&oacute;n indicada, o UTF-8 si el nombre no es v&aacute;lido. */
    public static Charset charsetOrUtf8(String nombre) {
        try {
            return Charset.forName(nombre);
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    //
    // Archivos recientes
    //
    public void addRecentFile(Path path) {
        String ruta = path.toAbsolutePath().normalize().toString();
        recentFiles.remove(ruta);
        recentFiles.add(0, ruta);
        recortarRecientes();
    }

    public void removeRecentFile(String ruta) {
        recentFiles.remove(ruta);
    }

    public void clearRecentFiles() {
        recentFiles.clear();
    }

    private void recortarRecientes() {
        int max = Math.max(0, maxRecentFiles.get());
        while (recentFiles.size() > max) {
            recentFiles.remove(recentFiles.size() - 1);
        }
    }

    //
    // Persistencia
    //
    /** Ruta del archivo de configuraci&oacute;n; respeta {@code LECTOR_CONFIG_DIR} si est&aacute; definida. */
    public static Path configFile() {
        String custom = System.getenv("LECTOR_CONFIG_DIR");
        Path base = (custom != null && !custom.isBlank())
                ? Path.of(custom)
                : Path.of(System.getProperty("user.home"), ".config", "lector-archivos");
        return base.resolve("settings.properties");
    }

    public void load() {
        Path file = configFile();
        if (!Files.isRegularFile(file)) {
            resolverTipografia();
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            resolverTipografia();
            return; // Configuracion ilegible: se sigue con los valores por omision.
        }

        theme.set(props.getProperty("theme", theme.get()));
        fontFamily.set(props.getProperty("fontFamily", fontFamily.get()));
        fontSize.set(entero(props.getProperty("fontSize"), fontSize.get(), TAMANO_FUENTE_MIN, TAMANO_FUENTE_MAX));
        defaultCharset.set(props.getProperty("defaultCharset", defaultCharset.get()));
        wrapText.set(Boolean.parseBoolean(props.getProperty("wrapText", String.valueOf(wrapText.get()))));
        showLineNumbers.set(Boolean.parseBoolean(props.getProperty("showLineNumbers", String.valueOf(showLineNumbers.get()))));
        detectCharset.set(Boolean.parseBoolean(props.getProperty("detectCharset", String.valueOf(detectCharset.get()))));
        maxRecentFiles.set(entero(props.getProperty("maxRecentFiles"), maxRecentFiles.get(), 0, 50));
        lastDirectory.set(props.getProperty("lastDirectory", ""));

        recentFiles.setAll(listaUnica(props.getProperty("recentFiles", "")));
        recortarRecientes();
        resolverTipografia();
    }

    /**
     * Completa la tipograf&iacute;a si no hay ninguna guardada.
     *
     * <p>Tambi&eacute;n cubre el caso de llevarse la configuraci&oacute;n de un
     * equipo a otro: si la familia guardada no existe aqu&iacute;, se sustituye
     * por una que s&iacute;, en vez de dejar el editor en una fuente cualquiera.</p>
     */
    private void resolverTipografia() {
        String actual = fontFamily.get();
        if (actual == null || actual.isBlank()) {
            fontFamily.set(tipografiaPorOmision());
            return;
        }
        try {
            if (!javafx.scene.text.Font.getFamilies().contains(actual) && !"Monospaced".equals(actual)) {
                fontFamily.set(tipografiaPorOmision());
            }
        } catch (RuntimeException e) {
            // Sin motor grafico no se puede comprobar; se respeta lo guardado.
        }
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("theme", theme.get());
        props.setProperty("fontFamily", fontFamily.get());
        props.setProperty("fontSize", String.valueOf(fontSize.get()));
        props.setProperty("defaultCharset", defaultCharset.get());
        props.setProperty("wrapText", String.valueOf(wrapText.get()));
        props.setProperty("showLineNumbers", String.valueOf(showLineNumbers.get()));
        props.setProperty("detectCharset", String.valueOf(detectCharset.get()));
        props.setProperty("maxRecentFiles", String.valueOf(maxRecentFiles.get()));
        props.setProperty("lastDirectory", lastDirectory.get() == null ? "" : lastDirectory.get());
        props.setProperty("recentFiles", String.join("\n", recentFiles));

        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Preferencias del Lector de archivos");
            }
        } catch (IOException e) {
            // No poder guardar las preferencias no debe impedir usar el programa.
            System.err.println("No se pudieron guardar las preferencias: " + e.getMessage());
        }
    }

    /** Devuelve los valores de f&aacute;brica sin tocar los archivos recientes. */
    public void restoreDefaults() {
        theme.set(Theme.CLARO.name());
        fontFamily.set(tipografiaPorOmision());
        fontSize.set(14);
        defaultCharset.set(StandardCharsets.UTF_8.name());
        wrapText.set(false);
        showLineNumbers.set(true);
        detectCharset.set(true);
        maxRecentFiles.set(10);
    }

    private static List<String> listaUnica(String crudo) {
        // LinkedHashSet para conservar el orden y descartar duplicados de golpe.
        return new ArrayList<>(new LinkedHashSet<>(
                crudo.lines().map(String::trim).filter(s -> !s.isEmpty()).toList()));
    }

    private static int entero(String crudo, int porOmision, int min, int max) {
        try {
            return Math.clamp(Integer.parseInt(crudo), min, max);
        } catch (RuntimeException e) {
            return porOmision;
        }
    }
}
