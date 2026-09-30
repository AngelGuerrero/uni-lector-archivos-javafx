package com.unadm.lector.service;

import com.unadm.lector.model.TextStats;
import com.unadm.lector.model.WordCount;
import javafx.concurrent.Task;

import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calcula metricas sobre un texto: conteos, promedios y frecuencia de palabras.
 */
public final class TextAnalyzer {

    /**
     * Palabras vacias del espanol: articulos, preposiciones, conjunciones y
     * pronombres que dominan cualquier conteo de frecuencias y aportan poco.
     */
    // Se agrupan por categoria gramatical para poder mantenerlas, y algunas
    // caen en dos grupos ("el" es articulo y pronombre, "esta" determinante y
    // verbo). Set.copyOf descarta esos duplicados; Set.of lanzaria excepcion.
    private static final Set<String> VACIAS = Set.copyOf(java.util.List.of(
            // Articulos y determinantes
            "el", "la", "lo", "los", "las", "un", "una", "uno", "unos", "unas",
            "este", "esta", "esto", "estos", "estas", "ese", "esa", "eso", "esos", "esas",
            "aquel", "aquella", "aquello", "aquellos", "aquellas",
            "mi", "mis", "tu", "tus", "su", "sus", "nuestro", "nuestra", "nuestros", "nuestras",
            "mismo", "misma", "mismos", "mismas", "tal", "tales", "cada", "todo", "toda",
            "todos", "todas", "otro", "otra", "otros", "otras",
            "alguno", "alguna", "algunos", "algunas", "algo", "nada", "poco", "poca",
            "pocos", "pocas", "mucho", "mucha", "muchos", "muchas", "tanto", "tanta",

            // Preposiciones y contracciones
            "a", "al", "ante", "bajo", "con", "contra", "de", "del", "desde", "durante",
            "en", "entre", "hacia", "hasta", "mediante", "para", "por", "segun", "sin",
            "sobre", "tras",

            // Conjunciones y nexos
            "y", "e", "o", "u", "ni", "pero", "sino", "porque", "pues", "aunque",
            "mientras", "si", "que", "como", "cuando", "donde", "cual", "cuales",
            "cuanto", "cuanta", "cuantos", "cuantas", "quien", "quienes", "cuyo", "cuya",

            // Pronombres
            "me", "te", "se", "nos", "os", "le", "les",
            "yo", "tu", "el", "ella", "ello", "ellos", "ellas",
            "nosotros", "nosotras", "vosotros", "vosotras", "usted", "ustedes",

            // Verbos auxiliares y copulativos de uso muy frecuente
            "ser", "es", "son", "era", "eran", "eres", "soy", "somos", "fue", "fueron",
            "sea", "sean", "sera", "seran", "seria",
            "estar", "esta", "estan", "estaba", "estaban", "estoy", "estamos",
            "haber", "ha", "han", "he", "hemos", "habia", "habian", "hay", "hubo",
            "tener", "tiene", "tienen", "tenia", "tenian",

            // Adverbios muy comunes
            "no", "si", "mas", "menos", "muy", "ya", "aun", "aqui", "alli", "ahi",
            "asi", "antes", "despues", "luego", "entonces", "siempre", "nunca",
            "tambien", "tampoco", "solo", "solamente", "ademas", "ahora", "bien"
    ));

    /** Una palabra es una secuencia de letras o digitos, admitiendo guion y apostrofo internos. */
    private static final Pattern PALABRA =
            Pattern.compile("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*");

    /** Fin de oracion: punto, signo de exclamacion, de interrogacion o puntos suspensivos. */
    private static final Pattern ORACION = Pattern.compile("[.!?…]+[\\s\"')\\]]*");

    /** Cuantas palabras distintas se conservan en la tabla de frecuencias. */
    private static final int MAX_FRECUENCIAS = 200;

    private TextAnalyzer() {
        // Clase utilitaria.
    }

    /** Analiza el texto en segundo plano; util para documentos grandes. */
    public static Task<TextStats> analyzeTask(String texto, Charset charset, boolean omitirVacias) {
        return new Task<>() {
            @Override
            protected TextStats call() {
                return analyze(texto, charset, omitirVacias);
            }
        };
    }

    /**
     * Calcula todas las metricas de una pasada.
     *
     * @param texto        contenido a analizar
     * @param charset      codificacion con la que se mide el tamano en bytes
     * @param omitirVacias si se excluyen las palabras vacias del conteo de frecuencias
     */
    public static TextStats analyze(String texto, Charset charset, boolean omitirVacias) {
        if (texto == null || texto.isEmpty()) {
            return TextStats.empty();
        }

        long caracteres = texto.length();
        long sinEspacios = texto.codePoints().filter(cp -> !Character.isWhitespace(cp)).count();

        String[] lineas = texto.split("\n", -1);
        long totalLineas = lineas.length;
        long lineasConTexto = 0;
        int lineaMasLarga = 0;
        long parrafos = 0;
        boolean dentroDeParrafo = false;

        for (String linea : lineas) {
            boolean vacia = linea.isBlank();
            if (!vacia) {
                lineasConTexto++;
                if (!dentroDeParrafo) {
                    parrafos++;
                    dentroDeParrafo = true;
                }
            } else {
                dentroDeParrafo = false;
            }
            lineaMasLarga = Math.max(lineaMasLarga, linea.length());
        }

        Map<String, Long> conteo = new HashMap<>();
        long totalPalabras = 0;

        Matcher m = PALABRA.matcher(texto);
        while (m.find()) {
            totalPalabras++;
            String clave = normalizar(m.group());
            if (!clave.isEmpty()) {
                conteo.merge(clave, 1L, Long::sum);
            }
        }

        long distintas = conteo.size();
        long sumaFrecuencias = omitirVacias
                ? conteo.entrySet().stream().filter(e -> !VACIAS.contains(e.getKey()))
                        .mapToLong(Map.Entry::getValue).sum()
                : totalPalabras;

        List<WordCount> frecuencias = new ArrayList<>();
        conteo.entrySet().stream()
                .filter(e -> !omitirVacias || !VACIAS.contains(e.getKey()))
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(MAX_FRECUENCIAS)
                .forEach(e -> frecuencias.add(new WordCount(
                        e.getKey(),
                        e.getValue(),
                        sumaFrecuencias == 0 ? 0 : e.getValue() * 100.0 / sumaFrecuencias)));

        long oraciones = contarOraciones(texto);
        long bytes = texto.getBytes(charset == null ? java.nio.charset.StandardCharsets.UTF_8 : charset).length;

        return new TextStats(
                caracteres, sinEspacios, totalPalabras, distintas,
                totalLineas, lineasConTexto, parrafos, oraciones,
                bytes, lineaMasLarga, List.copyOf(frecuencias));
    }

    private static long contarOraciones(String texto) {
        long oraciones = 0;
        Matcher m = ORACION.matcher(texto);
        int ultimoFin = 0;
        while (m.find()) {
            // Solo cuenta si hay contenido real antes del signo de puntuacion.
            if (!texto.substring(ultimoFin, m.start()).isBlank()) {
                oraciones++;
            }
            ultimoFin = m.end();
        }
        // Texto final sin puntuacion de cierre: sigue siendo una oracion.
        if (ultimoFin < texto.length() && !texto.substring(ultimoFin).isBlank()) {
            oraciones++;
        }
        return oraciones;
    }

    /** Marcador temporal fuera del rango de texto normal para proteger la enie. */
    private static final String ENIE_MARCA = "\u0001";

    /**
     * Pasa la palabra a minusculas y le quita los acentos, para que
     * "Codigo", "codigo" y "c&oacute;digo" cuenten como la misma.
     *
     * <p>La enie se aparta antes de descomponer el texto, porque no es una
     * "n con acento" sino una letra distinta del espanol; si no se protege,
     * "ano" y "a&ntilde;o" terminarian contando como la misma palabra.</p>
     */
    private static String normalizar(String palabra) {
        // Se compone primero (NFC) para que la enie sea un solo caracter aunque
        // el archivo la traiga como "n" + tilde combinante.
        String minusculas = Normalizer.normalize(palabra, Normalizer.Form.NFC)
                .toLowerCase(java.util.Locale.ROOT)
                .replace("ñ", ENIE_MARCA);
        String sinAcentos = Normalizer.normalize(minusculas, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinAcentos.replace(ENIE_MARCA, "ñ");
    }

    /** Indica si la palabra dada es una palabra vacia del espanol. */
    public static boolean esVacia(String palabra) {
        return VACIAS.contains(normalizar(palabra));
    }
}
