package com.proyectosw.sreservas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Getter 
@Setter 
@NoArgsConstructor 
public class Usuario {
    @Id @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank 
    @Size (min = 2, max = 100)
    @Column (nullable = false, length = 100)
    private String nombres;

    @NotBlank 
    @Size (min = 2, max = 100)
    @Column (nullable = false, length = 100)
    private String apellidos;

    @NotBlank 
    @Email (message = "El correo debe tener un formato válido")
    @Size (max = 100)
    @Column (nullable = false, length = 100, unique = true)
    private String correo;
    
    @Size (min = 2, max = 100)
    @Column (nullable = false, length = 100)
    private String clave;
    
}
