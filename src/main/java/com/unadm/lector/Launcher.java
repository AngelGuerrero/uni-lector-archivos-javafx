package com.unadm.lector;

/**
 * Punto de entrada real del programa.
 *
 * <p>Se mantiene separado de {@link App} a propósito: cuando la clase que
 * contiene {@code main} extiende {@code Application}, la JVM exige que JavaFX
 * venga como módulo y falla con "JavaFX runtime components are missing".
 * Con este lanzador intermedio el arranque funciona tanto desde el
 * module-path como desde el classpath.</p>
 */
public final class Launcher {

    private Launcher() {
        // Clase utilitaria: no se instancia.
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
