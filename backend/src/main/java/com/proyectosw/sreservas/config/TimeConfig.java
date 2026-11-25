package com.proyectosw.sreservas.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de negocio del complejo.
 *
 * <p>Las reservas guardan fecha y hora local (sin zona), por lo que todas las
 * reglas temporales deben compararse contra el reloj del complejo y no contra
 * el del servidor. En produccion el servidor corre en UTC: sin este reloj, una
 * reserva de las 16:00 en Lima se comparaba con las 16:00 UTC y se rechazaba
 * como si estuviera en el pasado.</p>
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock relojNegocio(@Value("${application.timezone:America/Lima}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
