package com.proyectosw.sreservas.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.proyectosw.sreservas.model.Cancha;
import com.proyectosw.sreservas.model.Reserva;
import com.proyectosw.sreservas.model.enums.EstadoReserva;
import com.proyectosw.sreservas.repository.CanchaRepository;
import com.proyectosw.sreservas.repository.ReservaRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class ReservaService {
    private final ReservaRepository repository;
    private final CanchaRepository canchaRepository;

    public List<Reserva> listarTodos(){
        return repository.findAll();
    }

    public Optional<Reserva> buscarPorId(Integer id) {
        return repository.findById(id);
    }

    @Transactional 
    public Reserva crear(Reserva reserva){
        if (reserva.getCancha() == null || reserva.getCancha().getId() == null) {
            throw new RuntimeException("La reserva debe indicar una cancha");
        }

        Cancha cancha = canchaRepository.findById(reserva.getCancha().getId())
                .orElseThrow(() -> new RuntimeException("Cancha no encontrada con id " + reserva.getCancha().getId()));
        reserva.setCancha(cancha);

        if (!reserva.getFechaHoraFin().isAfter(reserva.getFechaHoraInicio())) {
            throw new RuntimeException("La fecha de fin debe ser posterior a la de inicio");
        }

        reserva.setEstadoReserva(EstadoReserva.PENDIENTE);
        recalcularMonto(reserva);

        return repository.save(reserva);
    }

    @Transactional 
    public Reserva actualizar(Integer id, Reserva datos){
        Reserva existente = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con id " + id));

        if (datos.getFechaHoraInicio() != null) existente.setFechaHoraInicio(datos.getFechaHoraInicio());
        if (datos.getFechaHoraFin() != null)     existente.setFechaHoraFin(datos.getFechaHoraFin());

        if (datos.getCancha() != null && datos.getCancha().getId() != null) {
            Cancha cancha = canchaRepository.findById(datos.getCancha().getId())
                    .orElseThrow(() -> new RuntimeException("Cancha no encontrada con id " + datos.getCancha().getId()));
            existente.setCancha(cancha);
        }

        if (!existente.getFechaHoraFin().isAfter(existente.getFechaHoraInicio())) {
            throw new RuntimeException("La fecha de fin debe ser posterior a la de inicio");
        }

        recalcularMonto(existente);

        return repository.save(existente);      
    }

    private void recalcularMonto(Reserva reserva) {
        BigDecimal precioHora = reserva.getCancha().getPrecioPorHora();
        long horas = Duration.between(
                reserva.getFechaHoraInicio(),
                reserva.getFechaHoraFin()).toHours();
        reserva.setMontoTotal(precioHora.multiply(BigDecimal.valueOf(horas)));
    }
}
