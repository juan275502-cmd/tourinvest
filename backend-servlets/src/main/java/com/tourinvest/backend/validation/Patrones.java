package com.tourinvest.backend.validation;

/**
 * Expresiones regulares de validacion compartidas por los DTOs.
 *
 * <p>Viven aqui (y no escritas al pie de cada anotacion) para que el mismo
 * criterio se aplique en todos los endpoints y para que el frontend pueda
 * replicarlas sin inventar reglas distintas.</p>
 */
public final class Patrones {

    private Patrones() {
    }

    /**
     * Letras del español (incluye tildes, dieresis y enye) mas las basicas
     * ASCII. Nota: se escriben las formas acentuadas y NO acentuadas porque
     * Unicode no ofrece una clase abreviada para el Español.
     */
    public static final String LETRAS = "A-Za-zÁÉÍÓÚÜÖÑáéíóúüöñ";

    /**
     * Nombre o apellido: una o mas palabras separadas por espacio, guion o
     * apostrofo. NO admite digitos ni simbolos (rechaza "Juan123" o "Juan@",
     * ver casos PV-04 y PV-05) pero SI admite "María José" y "Núñez"
     * (ver casos PV-03 y PV-04).
     */
    public static final String NOMBRE = "^[" + LETRAS + "]+(?:[ '\\-]+[" + LETRAS + "]+)*$";

    /** Cedula: exclusivamente digitos, entre 6 y 20 caracteres (ver caso PV-14). */
    public static final String CEDULA = "^[0-9]{6,20}$";

    /**
     * Correo MAS estricto que el {@code @Email} de Hibernate, que acepta
     * "vale.lor@tourinvest" (dominio sin punto) y por eso dejaba pasar el
     * caso PV-07. Aqui el dominio DEBE terminar en una extension de al menos
     * dos letras: "vale.lor@tourinvest.com" (PV-06) y "vale.lor@tourinvest"
     * (PV-07) se separan correctamente.
     */
    public static final String CORREO =
            "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}$";

    /** Mensajes asociados, centralizados para que el frontend los copie tal cual. */
    public static final String MSG_NOMBRE =
            "Solo se permiten letras, espacios, guiones y apóstrofos (ej. María José).";
    public static final String MSG_APELLIDO =
            "Solo se permiten letras, espacios, guiones y apóstrofos (ej. Pérez Gómez).";
    public static final String MSG_CEDULA = "La cédula debe tener entre 6 y 20 dígitos numéricos.";
    public static final String MSG_CORREO = "El correo no tiene un formato válido (ej. usuario@dominio.com).";
}