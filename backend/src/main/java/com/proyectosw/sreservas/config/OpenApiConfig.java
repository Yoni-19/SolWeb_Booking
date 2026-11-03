package com.proyectosw.sreservas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Configuracion del contrato OpenAPI que springdoc genera en runtime.
 *
 * <p>La autenticacion normal es la cookie httpOnly {@code access_token} que setea
 * {@code POST /api/auth/login}: Swagger UI la envia sola porque corre en el mismo
 * host. El esquema {@code bearerAuth} queda documentado porque el filtro tambien
 * acepta {@code Authorization: Bearer ...} para Postman y clientes de linea de comandos.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_BEARER = "bearerAuth";
    private static final String ESQUEMA_COOKIE = "cookieAuth";

    @Bean
    OpenAPI apiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sistema Complejo Deportivo — API")
                        .description("Gestion de canchas, reservas, cobros y usuarios. "
                                + "POST /api/auth/login guarda el JWT en una cookie httpOnly; los endpoints "
                                + "protegidos la leen automaticamente.")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_COOKIE))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_COOKIE,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name(AuthCookies.NOMBRE)
                                        .description("Cookie httpOnly que setea POST /api/auth/login."))
                        .addSecuritySchemes(ESQUEMA_BEARER,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Alternativa a la cookie para Postman o scripts "
                                                + "(sin la palabra Bearer).")));
    }
}
