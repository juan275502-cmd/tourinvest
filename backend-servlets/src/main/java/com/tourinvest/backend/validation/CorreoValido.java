package com.tourinvest.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Correo con formato estricto: exige el "@" Y una extension de dominio
 * ("usuario@dominio.com").
 *
 * <p>Existe porque el {@code @Email} de Hibernate es demasiado permisivo:
 * acepta "vale.lor@tourinvest" (caso PV-07) y el usuario terminaba viendo
 * "Correo o contrasena incorrectos" en vez de un error de formato claro.</p>
 */
@Documented
@Constraint(validatedBy = CorreoValidoValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CorreoValido {

    String message() default Patrones.MSG_CORREO;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}