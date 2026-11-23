package com.proyectosw.sreservas.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.proyectosw.sreservas.dto.FranjaOcupadaResponse;
import com.proyectosw.sreservas.dto.ReservaEstadoRequest;
import com.proyectosw.sreservas.dto.ReservaRequest;
import com.proyectosw.sreservas.dto.ReservaResponse;
import com.proyectosw.sreservas.model.Pago;
import com.proyectosw.sreservas.model.Reserva;
import com.proyectosw.sreservas.model.enums.EstadoReserva;
import com.proyectosw.sreservas.service.PagoService;
import com.proyectosw.sreservas.service.ReservaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping ("/api/reservas")
@Tag(name = "Reservas", description = "Reservas de canchas y su ciclo de vida")
public class ReservaRestController {
    private final ReservaService service;
    private final PagoService pagoService;

    public ReservaRestController(final ReservaService service, final PagoService pagoService) {
        this.service = service;
        this.pagoService = pagoService;
    }

    @GetMapping
    @Operation(summary = "Lista las reservas paginadas",
            description = "ADMIN y RECEPCIONISTA ven todas; un CLIENTE solo las propias. "
                    + "Query params: page (base 0), size, sort, estado y fecha (yyyy-MM-dd), opcionales.")
    public ResponseEntity<Page<ReservaResponse>> listarTodos(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) EstadoReserva estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        Page<Reserva> reservas = service.listarTodos(pageable, estado, fecha);
        Map<Integer, Pago> pagos = pagoService.buscarPorReservas(
                reservas.getContent().stream().map(Reserva::getId).toList());
        Page<ReservaResponse> pagina = reservas.map(
                reserva -> ReservaResponse.toResponse(reserva, pagos.get(reserva.getId())));
        return ResponseEntity.ok(pagina);
    }

    @GetMapping ("/disponibilidad")
    @Operation(summary = "Franjas ocupadas de una cancha en un dia",
            description = "Sirve al formulario de reserva para avisar los horarios tomados. "
                    + "Query params: canchaId (obligatorio) y fecha en formato yyyy-MM-dd.")
    public ResponseEntity<List<FranjaOcupadaResponse>> disponibilidad(
            @RequestParam("canchaId") Integer canchaId,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(service.listarOcupadas(canchaId, fecha).stream()
                .map(FranjaOcupadaResponse::toResponse)
                .toList());
    }

    @GetMapping ("/{id}")
    @Operation(summary = "Busca una reserva por id")
    public ResponseEntity<ReservaResponse> buscarPorId(@PathVariable Integer id){
        Reserva reserva = service.buscarPorId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No existe la reserva con id " + id));
        return ResponseEntity.ok(conPago(reserva));
    }

    @PostMapping
    @Operation(summary = "Crea una reserva",
            description = "El backend calcula estado y monto; el cliente reserva siempre para si mismo.")
    public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody ReservaRequest request){
        ReservaResponse nuevo = ReservaResponse.toResponse(service.crear(request.toEntity()));
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevo);
    }

    @PutMapping ("/{id}")
    @Operation(summary = "Reprograma una reserva",
            description = "Ignora el usuario del cuerpo y recalcula el monto.")
    public ResponseEntity<ReservaResponse> actualizar(@PathVariable Integer id, @Valid @RequestBody ReservaRequest request){
        return ResponseEntity.ok(conPago(service.actualizar(id, request.toEntity())));
    }

    @PatchMapping ("/{id}/estado")
    @Operation(summary = "Cambia el estado de una reserva",
            description = "CONFIRMADA -> EN_CURSO (check-in) y EN_CURSO -> COMPLETADA (check-out) "
                    + "son solo ADMIN y RECEPCIONISTA; requieren pago registrado y saldo cero. "
                    + "Cancelar: PENDIENTE/CONFIRMADA -> CANCELADA, tambien para el CLIENTE dueno.")
    public ResponseEntity<ReservaResponse> cambiarEstado(@PathVariable Integer id,
                                                         @Valid @RequestBody ReservaEstadoRequest request){
        return ResponseEntity.ok(conPago(service.cambiarEstado(id, request.estadoReserva())));
    }

    @DeleteMapping ("/{id}")
    @Operation(summary = "Elimina una reserva definitivamente (limpieza)",
            description = "Borrado fisico pensado para pruebas. La interfaz cancela con "
                    + "PATCH /api/reservas/{id}/estado para conservar el historial.")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id){
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** Reserva + pago 1:1 (si existe) para que la tabla muestre pagado y saldo. */
    private ReservaResponse conPago(Reserva reserva) {
        return ReservaResponse.toResponse(reserva,
                pagoService.buscarPorReserva(reserva.getId()).orElse(null));
    }
}
