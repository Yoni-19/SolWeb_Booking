package com.proyectosw.sreservas.model.enums;

/**
 * Rol del usuario dentro del sistema.
 *
 * ADMIN: control total (canchas, clientes, usuarios, roles).
 * RECEPCIONISTA: opera reservas y cobra, pero no gestiona canchas, clientes ni roles.
 * CLIENTE: accede unicamente a sus propias reservas.
 *
 * ENTRENADOR, MANTENIMIENTO y ARBITRO existen solo como datos para clasificar al
 * personal: no tienen pantallas ni permisos y no pueden iniciar sesion.
 */
public enum Rol {
    ADMIN, RECEPCIONISTA, CLIENTE, ENTRENADOR, MANTENIMIENTO, ARBITRO;

    /** Roles que pueden iniciar sesion y usar la aplicacion. */
    public boolean puedeIngresar() {
        return this == ADMIN || this == RECEPCIONISTA || this == CLIENTE;
    }

    /** Personal = todo lo que no es un cliente. */
    public boolean esPersonal() {
        return this != CLIENTE;
    }
}
