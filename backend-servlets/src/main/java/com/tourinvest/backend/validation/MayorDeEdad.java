package com.tourinvest.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * La fecha de nacimiento debe corresponder a una persona **mayor de edad**.
 *
 * <p>En Colombia la mayoría de edad se cumple a los 18 años (art. 234 del
 * Código Civil), así que la fecha admitida es como máximo
 * {@code hoy.minusYears(minima)}.</p>
 *
 * <p>Esta restricción solo mira la edad. Lo demás lo cubre el resto de
 * validaciones de la fecha, para no encadenar dos mensajes sobre el mismo
 * campo:</p>
 * <ul>
 *   <li>Obligatoriedad → {@code @NotNull}</li>
 *   <li>No puede ser hoy ni futura → {@code @Past}</li>
 *   <li>Debe existir en el calendario → el formato {@code AAAA-MM-DD} y la
 *       conversión a {@link java.time.LocalDate} ya rechazan "2023-02-30"</li>
 * </ul>
 */
@Documented
@Constraint(validatedBy = MayorDeEdadValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MayorDeEdad {

    /** Edad mínima en años. Por defecto la de la mayoría de edad: 18. */
    int minima() default 18;

    String message() default "Debes ser mayor de edad para registrarte (mínimo 18 años)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}