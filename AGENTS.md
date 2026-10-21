# AGENTS.md — sreservas (backend)

## Build y run

- **El build oficial es Maven.** `mvn` NO está en el PATH de esta máquina: usá el wrapper.
- `.\mvnw.cmd clean compile` — build completo (39 fuentes).
- `.\mvnw.cmd spring-boot:run` — levanta en `http://localhost:8080`.
- `.\mvnw.cmd test` — tests. Requieren MySQL arriba (el test es un `@SpringBootTest`).
- MySQL: `localhost:3306`, user `root`, pass `mysql`, base `db_complejoDeportivo`.
  El esquema y el seed (`src/main/resources/data.sql`) se crean solos.

## Trampa activa: Lombok + JDK 25

El LS de VS Code (`redhat.java`) compila con ECJ usando su Lombok embebido **1.18.39**, que
no soporta JDK 25 (soporte JDK 25 llegó en 1.18.40). Genera constructores sin `super()` →
`VerifyError: Constructor must call super() or this() before return`.

Estado actual:

- `.vscode/settings.json` trae `"java.jdt.ls.javac.enabled": "on"` → el LS compila con javac
  y usa el Lombok 1.18.46 del proyecto. **Requiere *Developer: Reload Window* para aplicar.**
- `java.autobuild.enabled` está en `false` a propósito: el IDE no debe pisar `target/classes`
  en cada guardado.

Si aparece ese `VerifyError`: **no es bug del código**. `.\mvnw.cmd clean compile` y seguí.
Para auditar el bytecode: `javap -c -p -cp target/classes com.proyectosw.sreservas.config.SecurityConfig`
— el constructor debe mostrar `invokespecial java/lang/Object."<init>"`.

## Convenciones

- Idioma del código y los comentarios: español. Nombres de dominio: `Cancha`, `Reserva`,
  `Pago`, `Usuario`, `EstadoReserva`, `TipoDeporte`. Endpoints en inglés/plural
  (`/api/canchas`, `/api/reservas`, `/api/pagos`, `/api/usuarios`).
- **Nunca serializar entidades**: todo sale por DTOs (`dto/`). `Usuario.clave` es el hash
  BCrypt y jamás debe viajar en una respuesta.
- Errores: `@RestControllerAdvice` centralizado + `server.error.include-message=always`.
- CORS: solo `http://localhost:4200` y `http://127.0.0.1:4200` (frontend Angular).
- Sin atributos de IA en comentarios ni commits. Commits en castellano, formato
  `tipo(ámbito): descripción` (`feat`, `fix`, `refactor`, `docs`, `chore`).
- Java 25 disponible en `C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot`.

## Reglas de negocio

- Solapamiento de reservas → `409 Conflict` (`ReservaRepository.existeSolapamiento`).
  `CANCELADA` no bloquea; al actualizar se excluye la propia reserva.
- Monto por **hora iniciada**: `horas = (minutos + 59) / 60`. No usar `Duration.toHours()`.
- `PUT /api/usuarios/{id}` acepta clave opcional (`UsuarioUpdateRequest`): si viene `null`,
  la clave no cambia.
