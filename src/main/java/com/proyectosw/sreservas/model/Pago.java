package com.proyectosw.sreservas.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyectosw.sreservas.model.enums.EstadoPago;
import com.proyectosw.sreservas.model.enums.MetodoPago;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity 
@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
public class Pago {
    @Id @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column (nullable = false)
    private BigDecimal monto;
    
    @Column (nullable = false)
    @Enumerated(EnumType.STRING)
    private MetodoPago metodoPago;
    
    @Column (nullable = false)
    @Enumerated(EnumType.STRING)
    private EstadoPago estadoPago;
    
    @Column (nullable = false)
    private LocalDateTime fechaPago;
    
    @OneToOne (fetch = FetchType.LAZY)
    @JoinColumn (name = "reserva_id", unique = true, nullable = false)
    private Reserva reserva;

}
