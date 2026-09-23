package com.proyectosw.sreservas.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.proyectosw.sreservas.model.Usuario;
import com.proyectosw.sreservas.repository.UsuarioRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class UsuarioService {
    private final UsuarioRepository repository;

    public List<Usuario> listarTodos() {
        return repository.findAll();
    }

    public Optional<Usuario> buscarPorId(Integer id) {
        return repository.findById(id);
    }

    @Transactional 
    public Usuario crear(Usuario usuario) {
        return repository.save(usuario);
    }

    @Transactional
    public Usuario actualizar(Integer id, Usuario datos) {
        Usuario existente = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id " + id));

        existente.setNombres(datos.getNombres());
        existente.setApellidos(datos.getApellidos());
        existente.setCorreo(datos.getCorreo());
        existente.setClave(datos.getClave());

        return repository.save(existente);
    }

    @Transactional
    public void eliminar(Integer id) {
        repository.deleteById(id);
    }
}
