package com.proyectosw.sreservas.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * Fija la forma JSON de las respuestas paginadas.
 *
 * <p>Sin esto, Spring serializa {@code PageImpl} tal cual y avisa en el log que esa
 * estructura no es estable (puede cambiar entre versiones). Con {@code VIA_DTO} el
 * contrato queda en {@code {"content": [...], "page": {"size", "number",
 * "totalElements", "totalPages"}}}.</p>
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebConfig {
}
