package com.proyectosw.sreservas.model;

import com.proyectosw.sreservas.model.enums.Rol;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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

    /**
     * Rol con el que opera el usuario. El `default` de la columna no es decorativo: sin el,
     * el `alter table` que agrega la columna deja `''` en las filas existentes y Hibernate
     * revienta al convertir `''` a enum.
     */
    @Enumerated (EnumType.STRING)
    @Column (name = "rol", nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'CLIENTE'")
    private Rol rol = Rol.CLIENTE;

}
