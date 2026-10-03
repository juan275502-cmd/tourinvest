package com.tourinvest.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida la politica de contrasenas definida en {@link PasswordPolicy}
 * (12 caracteres, 2 mayusculas, 2 minusculas, 2 numeros, 2 especiales).
 *
 * <p>Se aplica a los campos que RECIBEN una contrasena nueva: registro publico,
 * cambio de contrasena del perfil y alta/edicion de usuarios del administrador.
 * NO se aplica al login: ahi la contrasena solo se verifica contra el hash.</p>
 *
 * <p>Un valor vacio se considera valido para esta anotacion a proposito: de ese
 * caso se encarga {@code @NotBlank}, que aporta el mensaje especifico
 * ("La contrasena es obligatoria") del caso PV-10.</p>
 */
@Documented
@Constraint(validatedBy = ContrasenaSeguraValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ContrasenaSegura {

    String message() default "La contraseña no cumple la política de seguridad";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}