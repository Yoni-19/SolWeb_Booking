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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
public class Cancha {
    @Id @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column (nullable = false)
    private String nombre;

    @Column (nullable = false)
    @Enumerated(EnumType.STRING)
    private TipoDeporte deporte;

    @Column (nullable = false)
    private BigDecimal precioPorHora;

    @Column (nullable = false)
    private boolean activa;

}
