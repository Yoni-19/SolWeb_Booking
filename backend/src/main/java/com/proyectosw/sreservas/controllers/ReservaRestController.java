package com.proyectosw.sreservas.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.proyectosw.sreservas.dto.ReservaRequest;
import com.proyectosw.sreservas.dto.ReservaResponse;
import com.proyectosw.sreservas.model.enums.EstadoReserva;
import com.proyectosw.sreservas.service.ReservaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping ("/api/reservas")
@Tag(name = "Reservas", description = "Reservas de canchas y su ciclo de vida")
public class ReservaRestController {
    private final ReservaService service;

    public ReservaRestController(final ReservaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista las reservas paginadas",
            description = "ADMIN y RECEPCIONISTA ven todas; un CLIENTE solo las propias. "
                    + "Query params: page (base 0), size, sort y estado (opcional).")
    public ResponseEntity<Page<ReservaResponse>> listarTodos(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) EstadoReserva estado) {
        Page<ReservaResponse> pagina = service.listarTodos(pageable, estado)
                .map(ReservaResponse::toResponse);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping ("/{id}")
    @Operation(summary = "Busca una reserva por id")
    public ResponseEntity<ReservaResponse> buscarPorId(@PathVariable Integer id){
        return service.buscarPorId(id)
                .map(ReservaResponse::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la reserva con id " + id));
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
        return ResponseEntity.ok(ReservaResponse.toResponse(service.actualizar(id, request.toEntity())));
    }

    @DeleteMapping ("/{id}")
    @Operation(summary = "Cancela una reserva",
            description = "Un CLIENTE solo puede cancelar sus propias reservas.")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id){
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
