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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.proyectosw.sreservas.dto.UsuarioRequest;
import com.proyectosw.sreservas.dto.UsuarioResponse;
import com.proyectosw.sreservas.dto.UsuarioRolRequest;
import com.proyectosw.sreservas.dto.UsuarioUpdateRequest;
import com.proyectosw.sreservas.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping ("/api/usuarios")
@Tag(name = "Usuarios", description = "Gestion de usuarios y roles")
public class UsuarioRestController {
    private final UsuarioService service;

    public UsuarioRestController(final UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista los usuarios paginados",
            description = "Query params: page (base 0), size y sort.")
    public ResponseEntity<Page<UsuarioResponse>> listarTodos(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<UsuarioResponse> pagina = service.listarTodos(pageable)
                .map(UsuarioResponse::toResponse);
        return ResponseEntity.ok(pagina);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca un usuario por id")
    public ResponseEntity<UsuarioResponse> buscarPorId(@PathVariable Integer id) {
        return service.buscarPorId(id)
                .map(UsuarioResponse::toResponse)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el usuario con id " + id));
    }

    @PostMapping
    @Operation(summary = "Registro publico de un cliente",
            description = "No exige token y siempre crea el usuario con rol CLIENTE.")
    @SecurityRequirements
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest request) {
        UsuarioResponse nuevo = UsuarioResponse.toResponse(service.crear(request.toEntity()));
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevo);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza los datos de un usuario",
            description = "La clave es opcional: si no viene, no se modifica.")
    public ResponseEntity<UsuarioResponse> actualizar(@PathVariable Integer id,
                                                      @Valid @RequestBody UsuarioUpdateRequest request) {
        return ResponseEntity.ok(UsuarioResponse.toResponse(service.actualizar(id, request.toEntity())));
    }

    @PutMapping("/{id}/rol")
    @Operation(summary = "Cambia el rol de un usuario (solo ADMIN)")
    public ResponseEntity<UsuarioResponse> cambiarRol(@PathVariable Integer id,
                                                      @Valid @RequestBody UsuarioRolRequest request) {
        return ResponseEntity.ok(UsuarioResponse.toResponse(service.cambiarRol(id, request.rol())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Elimina un usuario sin reservas asociadas")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

}
