package com.proyectosw.sreservas.model;

import java.math.BigDecimal;

import com.proyectosw.sreservas.model.enums.TipoDeporte;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Getter 
@Setter 
@NoArgsConstructor 
public class Cancha {
    @Id @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank (message = "El nombre de la cancha es obligatorio")
    @Size (max = 100, message = "El nombre no puede superar los 100 caracteres")
    @Column (nullable = false)
    private String nombre;

    @NotNull (message = "El deporte es obligatorio")
    @Column (nullable = false)
    @Enumerated(EnumType.STRING)
    private TipoDeporte deporte;

    @NotNull (message = "El precio por hora es obligatorio")
    @DecimalMin (value = "0.0", inclusive = false, message = "El precio por hora debe ser mayor a cero")
    @Column (nullable = false)
    private BigDecimal precioPorHora;

    @Column (nullable = false)
    private boolean activa;

}
