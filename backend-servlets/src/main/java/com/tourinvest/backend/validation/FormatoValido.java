package com.tourinvest.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * El valor debe cumplir la expresion regular indicada.
 *
 * <p>Existe en lugar de usar {@code @Pattern} por una razon concreta: si un
 * campo obligatorio viene VACIO se dispararian las dos reglas y el mensaje
 * acabaria siendo "... El nombre es obligatorio. Solo se permiten letras ...".
 * Aqui el valor vacio se considera valido y de eso se encarga {@code @NotBlank},
 * de modo que cada error corresponde a una sola causa (ver caso PV-02).</p>
 *
 * <p>Se aplica a los campos de texto con dominio restringido: nombre, apellido
 * y cedula (ver {@link Patrones}).</p>
 */
@Documented
@Constraint(validatedBy = FormatoValidoValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface FormatoValido {

    /** Expresion regular que debe cumplir el valor (debe estar anclada con ^ $). */
    String regexp();

    /** Se llama "message" por convenio de Bean Validation; aquí va el texto en español. */
    String message() default "El valor no tiene el formato esperado";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}