package com.proyectosw.sreservas.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Reloj fijo para los tests de integracion.
 *
 * <p>Sin esto, los tests que reservan "para hoy" dependen de la hora real: si la
 * suite se corre despues del cierre (22:00), no queda ninguna franja futura y
 * fallan sin que haya un defecto en el codigo.</p>
 */
@TestConfiguration
public class RelojFijo {

    public static final ZoneId ZONA = ZoneId.of("America/Lima");
    /** Las 10:00: quedan franjas libres hasta el cierre y el check-in es valido. */
    public static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 25, 10, 0);

    @Bean
    @Primary
    Clock relojDePrueba() {
        return Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA);
    }
}
