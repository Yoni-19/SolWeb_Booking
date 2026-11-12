package com.proyectosw.sreservas.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.proyectosw.sreservas.model.Usuario;
import com.proyectosw.sreservas.model.enums.Rol;
import com.proyectosw.sreservas.repository.ReservaRepository;
import com.proyectosw.sreservas.repository.UsuarioRepository;

import jakarta.transaction.Transactional;

@Service
public class UsuarioService {
    private final UsuarioRepository repository;
    private final ReservaRepository reservaRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(final UsuarioRepository repository, final ReservaRepository reservaRepository,
            final PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.reservaRepository = reservaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Lista los usuarios visibles según el rol de quien consulta:
     * un RECEPCIONISTA solo ve CLIENTES (ni su propio usuario ni el staff);
     * un ADMIN ve a los demás usuarios (nunca su propio usuario) y puede filtrar
     * por rol o pedir únicamente al personal con {@code personal=true}.
     */
    public Page<Usuario> listar(Pageable pageable, Rol filtro, Boolean personal, Rol rolSolicitante,
            Integer idSolicitante) {
        if (Rol.RECEPCIONISTA.equals(rolSolicitante)) {
            return repository.findByRol(Rol.CLIENTE, pageable);
        }
        // personal=true gana sobre rol: el listado de personal es "todo lo que no es CLIENTE".
        if (Boolean.TRUE.equals(personal)) {
            return repository.findByRolNotAndIdNot(Rol.CLIENTE, idSolicitante, pageable);
        }
        return filtro != null
                ? repository.findByRolAndIdNot(filtro, idSolicitante, pageable)
                : repository.findAllByIdNot(idSolicitante, pageable);
    }

    /**
     * Un RECEPCIONISTA solo puede consultar clientes; el resto de los roles
     * (ADMIN) no tiene esta restricción.
     */
    public Optional<Usuario> buscarPorId(Integer id, Rol rolSolicitante) {
        Optional<Usuario> usuario = repository.findById(id);
        if (usuario.isPresent() && Rol.RECEPCIONISTA.equals(rolSolicitante)
                && !Rol.CLIENTE.equals(usuario.get().getRol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El recepcionista solo puede consultar clientes");
        }
        return usuario;
    }

    @Transactional 
    public Usuario crear(Usuario usuario) {
        // El alta es publica: la cuenta nace siempre como CLIENTE. El rol se cambia despues
        // con PUT /api/usuarios/{id}/rol, que solo puede usar un ADMIN.
        return guardarConRol(usuario, Rol.CLIENTE);
    }

    /**
     * Alta de personal desde el panel del ADMIN. A diferencia del registro publico,
     * exige un rol de personal (nunca CLIENTE) y lo persiste directamente.
     */
    @Transactional
    public Usuario crearPersonal(Usuario usuario, Rol rol) {
        if (rol == null || !rol.esPersonal()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rol debe ser de personal");
        }
        return guardarConRol(usuario, rol);
    }

    private Usuario guardarConRol(Usuario usuario, Rol rol) {
        if (usuario.getClave() == null || usuario.getClave().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave es obligatoria");
        }
        if (repository.findByCorreo(usuario.getCorreo()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }
        usuario.setRol(rol);
        usuario.setClave(passwordEncoder.encode(usuario.getClave()));
        return repository.save(usuario);
    }

    @Transactional
    public Usuario actualizar(Integer id, Usuario datos) {
        Usuario existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe el usuario con id " + id));

        Optional<Usuario> conMismoCorreo = repository.findByCorreo(datos.getCorreo());
        if (conMismoCorreo.isPresent() && !conMismoCorreo.get().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }

        existente.setNombres(datos.getNombres());
        existente.setApellidos(datos.getApellidos());
        existente.setCorreo(datos.getCorreo());
        if (datos.getClave() != null)
            existente.setClave(passwordEncoder.encode(datos.getClave()));

        return repository.save(existente);
    }

    @Transactional
    public Usuario cambiarRol(Integer id, Rol rol) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe el usuario con id " + id));

        // Sin esta guarda, el ultimo ADMIN puede degradarse y nadie queda habilitado
        // para volver a asignar roles: el sistema quedaria trabado hasta tocar la base.
        if (Rol.ADMIN.equals(usuario.getRol()) && !Rol.ADMIN.equals(rol)
                && repository.countByRol(Rol.ADMIN) == 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede quitar el rol del último administrador");
        }

        usuario.setRol(rol);
        return repository.save(usuario);
    }

    @Transactional
    public void eliminar(Integer id) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe el usuario con id " + id));
        // Misma guarda que cambiarRol: sin esto, el único ADMIN podría borrarse
        // y el sistema quedaría sin nadie que pueda asignar roles.
        if (Rol.ADMIN.equals(usuario.getRol()) && repository.countByRol(Rol.ADMIN) == 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar el último administrador");
        }
        if (reservaRepository.existsByUsuario_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar el usuario porque tiene reservas asociadas");
        }
        repository.deleteById(id);
    }
}
