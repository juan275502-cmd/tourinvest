package com.tourinvest.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/** Aplica {@link Patrones#CORREO} (exige "@" y extension de dominio). */
public class CorreoValidoValidator implements ConstraintValidator<CorreoValido, String> {

    private static final Pattern CORREO = Pattern.compile(Patrones.CORREO);

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        if (valor == null || valor.isBlank()) {
            return true; // campo vacio: lo reporta @NotBlank con su propio mensaje
        }
        return CORREO.matcher(valor.trim()).matches();
    }
}