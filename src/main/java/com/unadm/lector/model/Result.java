package com.unadm.lector.model;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Resultado de una operación que puede fallar sin recurrir a excepciones.
 *
 * <p>Sustituye a la antigua clase {@code Response}: ahora es genérica,
 * inmutable y evita los {@code cast} manuales que hacían falta antes.</p>
 *
 * @param <T> tipo del dato que se devuelve cuando la operación fue exitosa
 */
public record Result<T>(boolean ok, T data, String message) {

    public static <T> Result<T> ok(T data, String message) {
        return new Result<>(true, data, message);
    }

    public static <T> Result<T> ok(String message) {
        return new Result<>(true, null, message);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(false, null, message);
    }

    public boolean failed() {
        return !ok;
    }

    public Optional<T> value() {
        return Optional.ofNullable(data);
    }

    /** Ejecuta la acción solo si la operación fue exitosa. Devuelve {@code this} para encadenar. */
    public Result<T> ifOk(Consumer<T> action) {
        if (ok) {
            action.accept(data);
        }
        return this;
    }

    /** Ejecuta la acción con el mensaje de error solo si la operación falló. */
    public Result<T> ifError(Consumer<String> action) {
        if (!ok) {
            action.accept(message);
        }
        return this;
    }
}
