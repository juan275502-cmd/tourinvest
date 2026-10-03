package com.tourinvest.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.Period;

/**
 * Comprueba que la fecha de nacimiento corresponda a alguien que ya cumplió la
 * edad mínima ({@link MayorDeEdad#minima()}, 18 años por defecto).
 *
 * <p>La edad se calcula con {@link Period#between(LocalDate, LocalDate)}, que
 * cuenta años <em>cumplidos</em>: quien cumple 18 dentro de un día todavía tiene
 * 17 y se rechaza, y quien cumple 18 hoy se acepta.</p>
 *
 * <p>Si la fecha es nula, o es de hoy o futura, el validador devuelve
 * {@code true}: esos casos ya los rechazan {@code @NotNull} y {@code @Past}, y
 * así el usuario no recibe dos mensajes encadenados por el mismo campo.</p>
 */
public class MayorDeEdadValidator implements ConstraintValidator<MayorDeEdad, LocalDate> {

    private int minima;

    @Override
    public void initialize(MayorDeEdad anotacion) {
        this.minima = anotacion.minima();
    }

    @Override
    public boolean isValid(LocalDate fechaNacimiento, ConstraintValidatorContext contexto) {
        if (fechaNacimiento == null) {
            return true; // lo comprueba @NotNull
        }

        LocalDate hoy = LocalDate.now();
        if (!fechaNacimiento.isBefore(hoy)) {
            return true; // hoy o futura: lo comprueba @Past
        }

        int aniosCumplidos = Period.between(fechaNacimiento, hoy).getYears();
        return aniosCumplidos >= minima;
    }
}