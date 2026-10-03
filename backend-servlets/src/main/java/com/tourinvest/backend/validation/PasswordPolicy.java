package com.tourinvest.backend.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Politica de contrasenas de TourInvest (requisitos de seguridad del proyecto).
 *
 * <p>Reglas, todas obligatorias y acumulativas:</p>
 * <ul>
 *   <li>Minimo {@value #LONGITUD_MINIMA} caracteres y maximo {@value #LONGITUD_MAXIMA}
 *       (72 es el limite que impone BCrypt: se trunca en silencio a partir de ahi).</li>
 *   <li>Al menos {@value #MINIMO_MAYUSCULAS} letras mayusculas.</li>
 *   <li>Al menos {@value #MINIMO_MINUSCULAS} letras minusculas.</li>
 *   <li>Al menos {@value #MINIMO_NUMEROS} digitos.</li>
 *   <li>Al menos {@value #MINIMO_ESPECIALES} caracteres especiales.</li>
 * </ul>
 *
 * <p>El conteo recorre la cadena por puntos de codigo ({@code codePoints()}), no
 * por {@code char}, para que las letras acentuadas ({@code Á}, {@code ñ}) se
 * contabilicen como letra y no como caracter especial. Un espacio en blanco
 * NUNCA cuenta como caracter especial.</p>
 *
 * <p>Esta clase no conoce Jakarta Validation: es logica pura y reutilizable, la
 * anotacion {@link ContrasenaSegura} es la que la conecta con los DTOs.</p>
 */
public final class PasswordPolicy {

    public static final int LONGITUD_MINIMA = 12;
    public static final int LONGITUD_MAXIMA = 72;
    public static final int MINIMO_MAYUSCULAS = 2;
    public static final int MINIMO_MINUSCULAS = 2;
    public static final int MINIMO_NUMEROS = 2;
    public static final int MINIMO_ESPECIALES = 2;

    private PasswordPolicy() {
    }

    /** {@code true} si la contrasena cumple la politica completa. */
    public static boolean esSegura(String contrasena) {
        return errores(contrasena).isEmpty();
    }

    /**
     * Requisitos incumplidos, cada uno ya redactado para mostrar al usuario
     * ("minimo 12 caracteres", "al menos 2 numeros", ...).
     *
     * <p>Un valor vacio o en blanco devuelve lista vacia a proposito: de ese
     * caso se encarga {@code @NotBlank}, que ya tiene su propio mensaje
     * ("La contrasena es obligatoria") y evita mensajes duplicados.</p>
     */
    public static List<String> errores(String contrasena) {
        List<String> fallos = new ArrayList<>();
        if (contrasena == null || contrasena.isBlank()) {
            return fallos;
        }

        int longitud = contrasena.length();
        int mayusculas = 0;
        int minusculas = 0;
        int digitos = 0;
        int especiales = 0;

        for (int punto : contrasena.codePoints().toArray()) {
            if (Character.isUpperCase(punto)) {
                mayusculas++;
            } else if (Character.isLowerCase(punto)) {
                minusculas++;
            } else if (Character.isDigit(punto)) {
                digitos++;
            } else if (!Character.isWhitespace(punto)) {
                especiales++;
            }
        }

        if (longitud < LONGITUD_MINIMA) {
            fallos.add("mínimo " + LONGITUD_MINIMA + " caracteres");
        }
        if (longitud > LONGITUD_MAXIMA) {
            fallos.add("máximo " + LONGITUD_MAXIMA + " caracteres");
        }
        if (mayusculas < MINIMO_MAYUSCULAS) {
            fallos.add("al menos " + MINIMO_MAYUSCULAS + " letras mayúsculas");
        }
        if (minusculas < MINIMO_MINUSCULAS) {
            fallos.add("al menos " + MINIMO_MINUSCULAS + " letras minúsculas");
        }
        if (digitos < MINIMO_NUMEROS) {
            fallos.add("al menos " + MINIMO_NUMEROS + " números");
        }
        if (especiales < MINIMO_ESPECIALES) {
            fallos.add("al menos " + MINIMO_ESPECIALES + " caracteres especiales");
        }

        return fallos;
    }

    /**
     * Mensaje listo para el usuario con TODOS los requisitos incumplidos, o
     * {@code null} si la contraseña cumple la política.
     */
    public static String mensajeError(String contrasena) {
        List<String> fallos = errores(contrasena);
        if (fallos.isEmpty()) {
            return null;
        }
        return "La contraseña debe cumplir: " + String.join("; ", fallos) + ".";
    }
}