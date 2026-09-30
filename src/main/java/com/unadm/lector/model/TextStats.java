package com.unadm.lector.model;

import java.util.List;

/**
 * Métricas calculadas sobre el contenido de un documento.
 *
 * @param characters         total de caracteres, espacios incluidos
 * @param charactersNoSpaces total de caracteres sin contar espacios en blanco
 * @param words              total de palabras
 * @param uniqueWords        palabras distintas (sin distinguir mayúsculas)
 * @param lines              total de líneas
 * @param nonEmptyLines      líneas que contienen algo además de espacios
 * @param paragraphs         bloques separados por una o más líneas en blanco
 * @param sentences          oraciones detectadas por . ! ? …
 * @param bytes              tamaño del texto codificado, en bytes
 * @param longestLine        número de caracteres de la línea más larga
 * @param frequencies        palabras ordenadas de mayor a menor frecuencia
 */
public record TextStats(
        long characters,
        long charactersNoSpaces,
        long words,
        long uniqueWords,
        long lines,
        long nonEmptyLines,
        long paragraphs,
        long sentences,
        long bytes,
        int longestLine,
        List<WordCount> frequencies
) {

    public static TextStats empty() {
        return new TextStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
    }

    public double averageWordLength() {
        return words == 0 ? 0 : (double) charactersNoSpaces / words;
    }

    public double averageWordsPerLine() {
        return nonEmptyLines == 0 ? 0 : (double) words / nonEmptyLines;
    }

    public double averageWordsPerSentence() {
        return sentences == 0 ? 0 : (double) words / sentences;
    }

    /** Riqueza léxica: proporción de palabras distintas sobre el total. */
    public double lexicalDiversity() {
        return words == 0 ? 0 : (double) uniqueWords / words;
    }
}
