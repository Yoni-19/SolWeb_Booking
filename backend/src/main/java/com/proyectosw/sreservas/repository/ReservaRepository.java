package com.proyectosw.sreservas.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.proyectosw.sreservas.model.Reserva;
import com.proyectosw.sreservas.model.enums.EstadoReserva;

public interface ReservaRepository extends JpaRepository<Reserva, Integer>{

    /**
     * True if a reservation other than {@code reservaPropiaId} (nullable) occupies
     * the same court over [inicio, fin]. CANCELADA never blocks the slot.
     */
    @Query("""
            select count(r) > 0
            from Reserva r
            where r.cancha.id = :canchaId
              and r.estadoReserva in :estados
              and r.fechaHoraInicio < :fin
              and r.fechaHoraFin > :inicio
              and (:reservaPropiaId is null or r.id <> :reservaPropiaId)
            """)
    boolean existeSolapamiento(@Param("canchaId") Integer canchaId,
                               @Param("reservaPropiaId") Integer reservaPropiaId,
                               @Param("estados") Collection<EstadoReserva> estados,
                               @Param("inicio") LocalDateTime inicio,
                               @Param("fin") LocalDateTime fin);

    /**
     * Reservations of one court that still occupy the slot within [desde, hasta).
     * Used by the booking form to show the taken hours before submitting.
     */
    @Query("""
            select r
            from Reserva r
            where r.cancha.id = :canchaId
              and r.estadoReserva in :estados
              and r.fechaHoraInicio < :hasta
              and r.fechaHoraFin > :desde
            order by r.fechaHoraInicio
            """)
    List<Reserva> buscarOcupadas(@Param("canchaId") Integer canchaId,
                                 @Param("estados") Collection<EstadoReserva> estados,
                                 @Param("desde") LocalDateTime desde,
                                 @Param("hasta") LocalDateTime hasta);

    /**
     * Listado paginado con filtros opcionales. {@code usuarioId} limita al dueno
     * (CLIENTE), {@code estado} filtra por estado, {@code excluirEstado} sirve para
     * ocultar las canceladas del cliente y {@code desde}/{@code hasta} recortan un dia.
     */
    @Query("""
            select r
            from Reserva r
            where r.usuario.id = coalesce(:usuarioId, r.usuario.id)
              and r.estadoReserva = coalesce(:estado, r.estadoReserva)
              and (:incluirCancelada = true or r.estadoReserva <> com.proyectosw.sreservas.model.enums.EstadoReserva.CANCELADA)
              and r.fechaHoraInicio >= :desde
              and r.fechaHoraInicio < :hasta
            """)
    Page<Reserva> buscar(@Param("usuarioId") Integer usuarioId,
                         @Param("estado") EstadoReserva estado,
                         @Param("incluirCancelada") boolean incluirCancelada,
                         @Param("desde") LocalDateTime desde,
                         @Param("hasta") LocalDateTime hasta,
                         Pageable pageable);

    /**
     * Marca como NO_ASISTIO las reservas confirmadas cuyo horario de fin ya paso.
     * Se ejecuta antes de listar para que el historial no muestre reservas vencidas
     * como si siguieran vigentes.
     */
    @Modifying
    @Query("""
            update Reserva r
               set r.estadoReserva = :noAsistio
             where r.estadoReserva = :confirmada
               and r.fechaHoraFin < :ahora
            """)
    int marcarNoAsistio(@Param("confirmada") EstadoReserva confirmada,
                        @Param("noAsistio") EstadoReserva noAsistio,
                        @Param("ahora") LocalDateTime ahora);

    /** True if the user has any reservation; blocks the deletion of the user. */
    boolean existsByUsuario_Id(Integer usuarioId);

    /** True if the court has any reservation; blocks the deletion of the court. */
    boolean existsByCancha_Id(Integer canchaId);

    /** Reservations of a single user: the client only sees their own. */
    Page<Reserva> findByUsuario_Id(Integer usuarioId, Pageable pageable);

    /** Same as above, narrowed to one state (used by the dashboard counters). */
    Page<Reserva> findByUsuario_IdAndEstadoReserva(Integer usuarioId, EstadoReserva estadoReserva, Pageable pageable);

    /** Reservations of one user excluding a state: a client does not see their cancelled ones. */
    Page<Reserva> findByUsuario_IdAndEstadoReservaNot(Integer usuarioId, EstadoReserva estadoReserva, Pageable pageable);

    /** All reservations in one state (staff only). */
    Page<Reserva> findByEstadoReserva(EstadoReserva estadoReserva, Pageable pageable);
}
