package com.proyectosw.sreservas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

public class Usuario {
    @Id @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column (nullable = false, length = 100)
    private String nombres;
    @Column (nullable = false, length = 100)
    private String apellidos;
    @Column (nullable = false, length = 100, unique = true)
    private String correo;
    @Column (nullable = false, length = 100)
    private String clave;
    @Column (nullable = false, length = 100)
    private String telefono;    
}
