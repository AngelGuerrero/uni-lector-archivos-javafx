package com.unadm.lector.model;

/**
 * Frecuencia de una palabra dentro del texto analizado.
 *
 * @param word       la palabra, ya normalizada a minúsculas
 * @param count      cuántas veces aparece
 * @param percentage porcentaje que representa sobre el total de palabras
 */
public record WordCount(String word, long count, double percentage) {
}
