package com.proyectosw.sreservas.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.proyectosw.sreservas.model.Cancha;
import com.proyectosw.sreservas.repository.CanchaRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class CanchaService {
    private final CanchaRepository repository;

    public List<Cancha> listarTodos(){
        return repository.findAll();
    }

    public Optional<Cancha> buscarPorId(Integer id) {
        return repository.findById(id);
    }

    @Transactional
    public Cancha crear(Cancha cancha) {
        // Validación de negocio mínima (los @NotBlank no existen acá)
        if (cancha.getNombre() == null || cancha.getNombre().isBlank()) {
            throw new RuntimeException("El nombre de la cancha es obligatorio");
        }
        if (cancha.getPrecioPorHora() == null || cancha.getPrecioPorHora().signum() <= 0) {
            throw new RuntimeException("El precio por hora debe ser mayor a cero");
        }
        return repository.save(cancha);
    }

    @Transactional
    public Cancha actualizar(Integer id, Cancha datos) {
        Cancha existente = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cancha no encontrada con id " + id));
        if (datos.getNombre() != null) existente.setNombre(datos.getNombre());
        if (datos.getDeporte() != null) existente.setDeporte(datos.getDeporte());
        if (datos.getPrecioPorHora() != null) existente.setPrecioPorHora(datos.getPrecioPorHora());
        existente.setActiva(datos.isActiva());
        return repository.save(existente);
    }

    @Transactional
    public void eliminar(Integer id) { 
        repository.deleteById(id); 
    }
    
}
