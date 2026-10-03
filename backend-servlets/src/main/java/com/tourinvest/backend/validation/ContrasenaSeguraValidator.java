package com.tourinvest.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Traduce la politica de {@link PasswordPolicy} a una violacion de Bean
 * Validation. En lugar de un unico mensaje generico, publica el detalle de
 * TODOS los requisitos incumplidos ("minimo 12 caracteres; al menos 2 numeros;
 * ..."), que es lo que el usuario necesita para corregir la contrasena.
 */
public class ContrasenaSeguraValidator implements ConstraintValidator<ContrasenaSegura, String> {

    @Override
    public boolean isValid(String contrasena, ConstraintValidatorContext contexto) {
        String mensaje = PasswordPolicy.mensajeError(contrasena);
        if (mensaje == null) {
            return true;
        }

        contexto.disableDefaultConstraintViolation();
        contexto.buildConstraintViolationWithTemplate(mensaje).addConstraintViolation();
        return false;
    }
}