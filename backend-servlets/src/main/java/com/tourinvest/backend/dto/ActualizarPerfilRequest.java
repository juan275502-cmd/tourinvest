package com.tourinvest.backend.dto;

import com.tourinvest.backend.validation.CorreoValido;
import com.tourinvest.backend.validation.FormatoValido;
import com.tourinvest.backend.validation.MayorDeEdad;
import com.tourinvest.backend.validation.Patrones;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class ActualizarPerfilRequest extends DatosPersonalesRequest {
}