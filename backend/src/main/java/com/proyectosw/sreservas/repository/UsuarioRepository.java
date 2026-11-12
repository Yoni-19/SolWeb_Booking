package com.proyectosw.sreservas.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.proyectosw.sreservas.model.Usuario;
import com.proyectosw.sreservas.model.enums.Rol;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer>{
    Optional<Usuario> findByCorreo(String correo);

    Page<Usuario> findByRol(Rol rol, Pageable pageable);

    Page<Usuario> findAllByIdNot(Integer id, Pageable pageable);

    Page<Usuario> findByRolAndIdNot(Rol rol, Integer id, Pageable pageable);

    Page<Usuario> findByRolNotAndIdNot(Rol rol, Integer id, Pageable pageable);

    long countByRol(Rol rol);
}
