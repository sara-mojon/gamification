<!-- arquitectura_flujos_judge0.md -->

# Documentación Técnica: Arquitectura de Flujos (Judge0)

**Proyecto:** Plataforma de Evaluación de Código

**Módulo:** Motor de Evaluación (Judge0)

**Componentes:** API Server, Worker, Redis, PostgreSQL.

---

## 1. Diagramas de Secuencia

Ilustración del comportamiento del sistema ante solicitudes síncronas y asíncronas para la evaluación de código.

### A. Flujo ASÍNCRONO (`wait=false`)

_Escenario típico de producción: Alta concurrencia, el cliente no bloquea la conexión._

**Componentes:**

- **Cliente:** Frontend / Usuario.
- **Server:** Pod `judge0-server` (API Gateway).
- **Redis:** Cola de mensajería para desacoplamiento.
- **Worker:** Pod `judge0-worker` (Procesamiento con Wrapper de compatibilidad).
- **DB:** PostgreSQL (Persistencia de resultados).

```mermaid
sequenceDiagram
    participant User as 👤 Cliente
    participant Server as 🌐 Server API
    participant DB as 🐘 PostgreSQL
    participant Redis as 📮 Redis Queue
    participant Worker as 👷 Worker (Wrapper)

    Note over User, Server: 1. ENVÍO DEL TRABAJO
    User->>Server: POST /submissions (wait=false)
    Server->>DB: INSERT (Status: Pending, Code, Tests)
    DB-->>Server: Retorna ID (ej: 17)
    Server->>Redis: RPUSH { "job_id": 17 }
    Server-->>User: Retorna HTTP 200 OK + TOKEN
    Note right of User: El Cliente recibe Token y libera conexión.

    Note over Worker, Redis: 2. PROCESAMIENTO (Background)
    Worker->>Redis: BLPOP (¿Hay trabajo?)
    Redis-->>Worker: Tarea: ID 17
    Worker->>DB: SELECT * FROM submissions WHERE id=17
    DB-->>Worker: Entrega Código + Tests
    Note over Worker: ⚙️ Ejecuta "Wrapper Cirujano" + Python
    Worker->>DB: UPDATE (Status: Accepted, Stdout: "OK")

    Note over User, Server: 3. RECOGIDA (Polling)
    loop Cada X segundos
        User->>Server: GET /submissions/TOKEN
        Server->>DB: SELECT status, stdout...
        DB-->>Server: Datos
        Server-->>User: JSON Resultado
    end
```

### B. Flujo SÍNCRONO (`wait=true`)

_Escenario de pruebas o baja latencia: El servidor procesa la petición en el mismo hilo HTTP._

```mermaid
sequenceDiagram
    participant User as 👤 Cliente
    participant Server as 🌐 Server API (con Wrapper)
    participant DB as 🐘 PostgreSQL

    Note over User, Server: CONEXIÓN BLOQUEADA
    User->>Server: POST /submissions (wait=true)
    Note right of User: ⏳ El navegador espera respuesta...

    Server->>DB: INSERT (Status: Pending)

    Note over Server: ⚠️ BYPASS DE WORKER:
    Note over Server: El Server invoca directamente al Wrapper + Python

    Server->>DB: UPDATE (Status: Accepted, Stdout...)

    Server-->>User: Retorna HTTP 200 OK + JSON RESULTADO
    Note right of User: Recepción inmediata del resultado.
```

---

## 2. Referencia de API

Ejemplos de consumo para integración con Frontend.

### A. Enviar Solución (Modo Síncrono)

_Endpoint:_ `POST /submissions/?base64_encoded=true&wait=true`

```bash
curl -X POST "http://<JUDGE0_HOST>:2358/submissions/?base64_encoded=true&wait=true" \
     -H "Content-Type: application/json" \
     -d '{
    "language_id": 71,
    "source_code": "aW1wb3J0IHVuaXR0ZXN0CgojIC0tLSBDT0RJR08g..."
}'
```

### B. Consultar Resultado (Modo Asíncrono)

_Endpoint:_ `GET /submissions/<TOKEN>?base64_encoded=true`

```json
{
  "stdout": null,
  "time": "0.067",
  "memory": 0,
  "stderr": "Li4uLi4uCi0tLS0tLS0t...",
  "status": {
    "id": 3,
    "description": "Accepted"
  }
}
```
