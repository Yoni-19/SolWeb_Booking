package com.proyectosw.sreservas.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyectosw.sreservas.model.Usuario;
import com.proyectosw.sreservas.model.enums.Rol;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer>{
    Optional<Usuario> findByCorreo(String correo);

    Page<Usuario> findByRol(Rol rol, Pageable pageable);

    Page<Usuario> findAllByIdNot(Integer id, Pageable pageable);

    Page<Usuario> findByRolAndIdNot(Rol rol, Integer id, Pageable pageable);

    Page<Usuario> findByRolNotAndIdNot(Rol rol, Integer id, Pageable pageable);

    long countByRol(Rol rol);

    /**
     * Búsqueda por nombres, apellidos o correo. {@code rol} opcional limita el
     * resultado; {@code rolExcluido} sirve para el listado de personal (todo lo
     * que no es CLIENTE). El propio usuario solicitante nunca aparece.
     */
    @Query("""
            select u from Usuario u
            where u.id <> :idSolicitante
              and (:rol is null or u.rol = :rol)
              and (:rolExcluido is null or u.rol <> :rolExcluido)
              and (lower(u.nombres) like :patron
                   or lower(u.apellidos) like :patron
                   or lower(u.correo) like :patron)
            """)
    Page<Usuario> buscar(@Param("idSolicitante") Integer idSolicitante,
                         @Param("rol") Rol rol,
                         @Param("rolExcluido") Rol rolExcluido,
                         @Param("patron") String patron,
                         Pageable pageable);
}
