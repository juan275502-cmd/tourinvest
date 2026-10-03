package com.tourinvest.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Aplica la expresion regular de {@link FormatoValido#regexp()}.
 *
 * <p>Un valor nulo, vacio o solo con espacios se considera VALIDO: la
 * obligatoriedad la comprueba {@code @NotBlank}, con su propio mensaje. Asi un
 * campo vacio produce un unico error legible en vez de dos encadenados.</p>
 */
public class FormatoValidoValidator implements ConstraintValidator<FormatoValido, String> {

    private Pattern patron;

    @Override
    public void initialize(FormatoValido anotacion) {
        this.patron = Pattern.compile(anotacion.regexp());
    }

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        if (valor == null || valor.isBlank()) {
            return true;
        }
        return patron.matcher(valor.trim()).matches();
    }
}