package com.proyectosw.sreservas.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.proyectosw.sreservas.model.Pago;

public interface PagoRepository extends JpaRepository<Pago, Integer> {

    /** Un pago por reserva (relacion 1:1), asi que alcanza con buscar por id de reserva. */
    Optional<Pago> findByReserva_Id(Integer reservaId);

    /** Carga en lote los pagos de una pagina de reservas y evita el N+1. */
    List<Pago> findByReserva_IdIn(Collection<Integer> reservaIds);
}
