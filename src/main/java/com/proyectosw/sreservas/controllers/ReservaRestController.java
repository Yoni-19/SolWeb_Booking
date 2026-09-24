package com.proyectosw.sreservas.controllers;

import java.util.List;

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

import com.proyectosw.sreservas.model.Reserva;
import com.proyectosw.sreservas.service.ReservaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping ("/api/reservas")
@RequiredArgsConstructor 
public class ReservaRestController {
    private final ReservaService service;

    @GetMapping 
    public ResponseEntity<List<Reserva>> listarTodos(){
        return ResponseEntity.ok(service.listarTodos());
    }

    @GetMapping ("/{id}")
    public ResponseEntity<Reserva> buscarPorId(@PathVariable Integer id){
        return service.buscarPorId(id).map(ResponseEntity::ok).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la reserva con id " + id));
    }

    @PostMapping 
    public ResponseEntity<Reserva> crear(@Valid @RequestBody Reserva reserva){
        Reserva nuevo = service.crear(reserva);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevo); 
    }

    @PutMapping ("/{id}")
    public ResponseEntity<Reserva> actualizar(@PathVariable Integer id, @RequestBody Reserva reserva){
        return ResponseEntity.ok(service.actualizar(id, reserva));
    }

    @DeleteMapping ("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id){
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
