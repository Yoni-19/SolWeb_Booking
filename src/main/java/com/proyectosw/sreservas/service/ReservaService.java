package com.proyectosw.sreservas.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.proyectosw.sreservas.model.Cancha;
import com.proyectosw.sreservas.model.Reserva;
import com.proyectosw.sreservas.model.Usuario;
import com.proyectosw.sreservas.model.enums.EstadoReserva;
import com.proyectosw.sreservas.repository.CanchaRepository;
import com.proyectosw.sreservas.repository.ReservaRepository;
import com.proyectosw.sreservas.repository.UsuarioRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class ReservaService {
    private final ReservaRepository repository;
    private final CanchaRepository canchaRepository;
    private final UsuarioRepository usuarioRepository;

    public List<Reserva> listarTodos(){
        return repository.findAll();
    }

    public Optional<Reserva> buscarPorId(Integer id) {
        return repository.findById(id);
    }

    @Transactional 
    public Reserva crear(Reserva reserva){
        if (reserva.getCancha() == null || reserva.getCancha().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La reserva debe indicar una cancha");
        }
        if (reserva.getUsuario() == null || reserva.getUsuario().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La reserva debe indicar un usuario");
        }

        Cancha cancha = canchaRepository.findById(reserva.getCancha().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe la cancha con id " + reserva.getCancha().getId()));
        if (!cancha.isActiva()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cancha " + cancha.getNombre() + " está inactiva y no se puede reservar");
        }
        reserva.setCancha(cancha);

        Usuario usuario = usuarioRepository.findById(reserva.getUsuario().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe el usuario con id " + reserva.getUsuario().getId()));
        reserva.setUsuario(usuario);

        if (!reserva.getFechaHoraFin().isAfter(reserva.getFechaHoraInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser posterior a la de inicio");
        }

        reserva.setEstadoReserva(EstadoReserva.PENDIENTE);
        recalcularMonto(reserva);

        return repository.save(reserva);
    }

    @Transactional 
    public Reserva actualizar(Integer id, Reserva datos){
        Reserva existente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe la reserva con id " + id));

        if (datos.getFechaHoraInicio() != null) existente.setFechaHoraInicio(datos.getFechaHoraInicio());
        if (datos.getFechaHoraFin() != null)     existente.setFechaHoraFin(datos.getFechaHoraFin());

        if (datos.getCancha() != null && datos.getCancha().getId() != null) {
            Cancha cancha = canchaRepository.findById(datos.getCancha().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe la cancha con id " + datos.getCancha().getId()));
            if (!cancha.isActiva()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cancha " + cancha.getNombre() + " está inactiva y no se puede reservar");
            }

            existente.setCancha(cancha);
        }

        if (!existente.getFechaHoraFin().isAfter(existente.getFechaHoraInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser posterior a la de inicio");
        }

        recalcularMonto(existente);

        return repository.save(existente);      
    }

    @Transactional
    public void eliminar(Integer id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No existe reserva con id " + id);
        }
        repository.deleteById(id);
    }

    private void recalcularMonto(Reserva reserva) {
        BigDecimal precioHora = reserva.getCancha().getPrecioPorHora();
        long horas = Duration.between(
                reserva.getFechaHoraInicio(),
                reserva.getFechaHoraFin()).toHours();
        reserva.setMontoTotal(precioHora.multiply(BigDecimal.valueOf(horas)));
    }
}
