package com.unadm.lector.service;

import javafx.concurrent.Task;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lectura y escritura de archivos de texto.
 *
 * <p>Las operaciones se exponen como {@link Task} para que la interfaz no se
 * congele con archivos grandes: el hilo de JavaFX solo recibe el resultado.
 * Este era uno de los defectos de la version anterior, que leia el archivo
 * completo dentro del manejador del boton.</p>
 */
public final class FileService {

    /** Tamano maximo que el editor acepta abrir (16 MiB). */
    public static final long TAMANO_MAXIMO = 16L * 1024 * 1024;

    /** Bytes que se inspeccionan para adivinar la codificacion o detectar binarios. */
    private static final int MUESTRA = 64 * 1024;

    private FileService() {
        // Clase utilitaria.
    }

    /**
     * Contenido de un archivo ya cargado en memoria.
     *
     * @param path     ruta del archivo leido
     * @param content  texto completo, con los saltos de linea normalizados a {@code \n}
     * @param charset  codificacion usada para decodificarlo
     * @param bytes    tamano en disco
     * @param hadBom   si el archivo empezaba con una marca de orden de bytes
     */
    public record LoadedFile(Path path, String content, Charset charset, long bytes, boolean hadBom) {
    }

    //
    // Carga
    //

    /**
     * Crea la tarea que lee un archivo de texto.
     *
     * @param path    archivo a leer
     * @param forzada codificacion a usar; si es {@code null} se detecta automaticamente
     */
    public static Task<LoadedFile> loadTask(Path path, Charset forzada) {
        return new Task<>() {
            @Override
            protected LoadedFile call() throws Exception {
                updateMessage("Comprobando " + path.getFileName() + "...");

                if (!Files.isRegularFile(path)) {
                    throw new IOException("La ruta no corresponde a un archivo: " + path);
                }
                if (!Files.isReadable(path)) {
                    throw new IOException("No hay permisos de lectura sobre " + path.getFileName());
                }

                long tamano = Files.size(path);
                if (tamano > TAMANO_MAXIMO) {
                    throw new IOException("El archivo pesa " + formatSize(tamano)
                            + " y el limite es " + formatSize(TAMANO_MAXIMO) + ".");
                }

                byte[] muestra = muestra(path);
                if (esBinario(muestra)) {
                    throw new IOException("El archivo parece binario, no texto. "
                            + "Se encontraron bytes nulos en los primeros " + muestra.length + " bytes.");
                }

                updateProgress(1, 4);
                updateMessage("Detectando codificacion...");

                Charset bom = charsetDeBom(muestra);
                Charset charset = forzada != null ? forzada
                        : (bom != null ? bom : detectarCharset(muestra));

                updateProgress(2, 4);
                updateMessage("Leyendo " + formatSize(tamano) + "...");

                byte[] datos = Files.readAllBytes(path);
                if (isCancelled()) {
                    return null;
                }

                updateProgress(3, 4);
                updateMessage("Decodificando...");

                boolean tieneBom = bom != null;
                String texto = decodificar(datos, charset, tieneBom);

                updateProgress(4, 4);
                updateMessage("Listo");
                return new LoadedFile(path, texto, charset, tamano, tieneBom);
            }
        };
    }

    /** Crea la tarea que escribe el contenido en disco. */
    public static Task<Path> saveTask(Path path, String content, Charset charset, boolean withBom) {
        return new Task<>() {
            @Override
            protected Path call() throws Exception {
                updateMessage("Guardando " + path.getFileName() + "...");

                Path padre = path.toAbsolutePath().getParent();
                if (padre != null) {
                    Files.createDirectories(padre);
                }

                byte[] cuerpo = content.getBytes(charset);
                byte[] prefijo = withBom ? bomDe(charset) : new byte[0];

                byte[] salida = new byte[prefijo.length + cuerpo.length];
                System.arraycopy(prefijo, 0, salida, 0, prefijo.length);
                System.arraycopy(cuerpo, 0, salida, prefijo.length, cuerpo.length);

                Files.write(path, salida);

                updateMessage("Guardado");
                return path;
            }
        };
    }

    //
    // Deteccion
    //

    private static byte[] muestra(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return in.readNBytes(MUESTRA);
        }
    }

    /**
     * Un archivo se considera binario si contiene bytes nulos, que no aparecen
     * en texto plano de un solo byte ni en UTF-8.
     */
    private static boolean esBinario(byte[] muestra) {
        // UTF-16 legitimamente contiene ceros: si hay BOM de UTF-16 no es binario.
        if (charsetDeBom(muestra) != null) {
            return false;
        }
        for (byte b : muestra) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    /** Devuelve la codificacion indicada por la marca de orden de bytes, o {@code null}. */
    private static Charset charsetDeBom(byte[] datos) {
        if (datos.length >= 3
                && (datos[0] & 0xFF) == 0xEF && (datos[1] & 0xFF) == 0xBB && (datos[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (datos.length >= 2 && (datos[0] & 0xFF) == 0xFF && (datos[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        if (datos.length >= 2 && (datos[0] & 0xFF) == 0xFE && (datos[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        return null;
    }

    private static byte[] bomDe(Charset charset) {
        if (StandardCharsets.UTF_8.equals(charset)) {
            return new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        }
        if (StandardCharsets.UTF_16LE.equals(charset)) {
            return new byte[]{(byte) 0xFF, (byte) 0xFE};
        }
        if (StandardCharsets.UTF_16BE.equals(charset)) {
            return new byte[]{(byte) 0xFE, (byte) 0xFF};
        }
        return new byte[0];
    }

    /**
     * Adivina la codificacion sin BOM: si la muestra decodifica como UTF-8
     * estricto se asume UTF-8; si no, se usa la codificacion configurada.
     */
    private static Charset detectarCharset(byte[] muestra) {
        if (!Settings.get().detectCharsetProperty().get()) {
            return Settings.get().getDefaultCharset();
        }
        return esUtf8Valido(muestra) ? StandardCharsets.UTF_8 : Settings.get().getDefaultCharset();
    }

    private static boolean esUtf8Valido(byte[] datos) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(datos));
            return true;
        } catch (CharacterCodingException e) {
            // La muestra puede cortar un caracter multibyte por la mitad; en ese
            // caso se reintenta recortando los ultimos bytes incompletos.
            return datos.length >= MUESTRA && esUtf8ValidoRecortado(decoder, datos);
        }
    }

    private static boolean esUtf8ValidoRecortado(CharsetDecoder decoder, byte[] datos) {
        for (int recorte = 1; recorte <= 3 && recorte < datos.length; recorte++) {
            decoder.reset();
            try {
                decoder.decode(ByteBuffer.wrap(datos, 0, datos.length - recorte));
                return true;
            } catch (CharacterCodingException ignored) {
                // Se prueba con un byte menos.
            }
        }
        return false;
    }

    /**
     * Decodifica los bytes reemplazando lo que no sea valido, quita el BOM y
     * normaliza CRLF y CR sueltos a LF para que el editor no muestre basura.
     */
    private static String decodificar(byte[] datos, Charset charset, boolean tieneBom) {
        int desplazamiento = tieneBom ? bomDe(charset).length : 0;

        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        CharBuffer buffer;
        try {
            buffer = decoder.decode(ByteBuffer.wrap(datos, desplazamiento, datos.length - desplazamiento));
        } catch (CharacterCodingException e) {
            // Con REPLACE no deberia ocurrir, pero se cubre por seguridad.
            buffer = CharBuffer.wrap(new String(datos, desplazamiento, datos.length - desplazamiento, charset));
        }

        return buffer.toString().replace("\r\n", "\n").replace('\r', '\n');
    }

    //
    // Utilidades
    //

    /** Formatea un tamano en bytes de forma legible (B, KB, MB, GB). */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] unidades = {"KB", "MB", "GB", "TB"};
        double valor = bytes;
        int i = -1;
        while (valor >= 1024 && i < unidades.length - 1) {
            valor /= 1024;
            i++;
        }
        return String.format("%.1f %s", valor, unidades[i]);
    }

    /** Mensaje de error legible a partir de la excepcion que lanzo una tarea. */
    public static String describir(Throwable error) {
        if (error == null) {
            return "Error desconocido";
        }
        Throwable causa = error.getCause() != null ? error.getCause() : error;
        String mensaje = causa.getMessage();
        if (mensaje == null || mensaje.isBlank()) {
            return causa.getClass().getSimpleName();
        }
        if (causa instanceof java.nio.file.NoSuchFileException) {
            return "No se encontro el archivo: " + mensaje;
        }
        if (causa instanceof java.nio.file.AccessDeniedException) {
            return "Acceso denegado: " + mensaje;
        }
        return mensaje;
    }
}
