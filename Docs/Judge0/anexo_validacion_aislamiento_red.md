<!-- anexo_validacion_aislamiento_red.md -->

# Informe Técnico: Validación de Aislamiento, Integridad y Red

**Objetivo:** Auditar las capacidades de _Sandboxing_ (Jaula) de los motores Piston y Judge0, cubriendo sistema de archivos, integridad del host y aislamiento de red.

---

## 1. Validación de Sistema de Archivos y Privilegios

### 1.1. Piston: Reconocimiento de Entorno

**Vector de Prueba:** Script de introspección (Usuarios, ENV, Filesystem).
**Resultados:**

- **Usuario:** `uid=60005`. Ejecución sin privilegios.
- **Secretos:** Las variables de entorno del host (`POSTGRES_PASSWORD`) no son visibles.
- **Escritura:** El sistema de archivos raíz es de **Solo Lectura** (`[Errno 13] Permission denied: '/hacked'`).

### 1.2. Judge0: Protección de Disco y Exfiltración

**Prueba A: Disk Bomb (Llenado de disco)**

- **Resultado:** `Me pararon: [Errno 27] File too large`.
- **Análisis:** La cuota de disco por proceso funciona correctamente, evitando DoS por saturación de almacenamiento.

**Prueba B: Exfiltración de Archivos Host**

- **Vector:** Intento de lectura de `/k3s/k3s-secrets.yaml`.
- **Resultado:** `[Errno 2] No such file or directory`.
- **Análisis:** El _Mount Namespace_ aísla completamente el contenedor; los archivos del host son invisibles.

### 1.3. Pruebas Avanzadas de Recursos y Sistema

**A. Agotamiento de Descriptores de Archivo (FD Exhaustion)**
**Objetivo:** Intentar colapsar la tabla de archivos del sistema abriendo miles de descriptores simultáneos.
**Resultados:**

- **Judge0:** `Detenido en 509 archivos. Razon: [Errno 24] Too many open files`.
- **Piston:** `Detenido en 2045 archivos`.
  **Análisis:** ✅ **SEGURO.** El límite `RLIMIT_NOFILE` está activo, impidiendo que un proceso malicioso consuma todos los recursos del Kernel.

**B. Ataque de Enlaces Simbólicos (Symlink Traversal)**
**Objetivo:** Evadir la jaula `chroot` mediante referencias relativas (`../../`) para leer archivos del host.
**Vector:** `os.symlink("../../../../../etc/passwd", "link")`
**Resultados:**

- Se logró leer el contenido de `/etc/passwd`.
- **Análisis Forense:** El contenido leído corresponde al archivo estándar del contenedor (`root:x:0:0...`), no al del host anfitrión.
  **Análisis:** ✅ **MITIGADO.** Aunque se permite la creación de enlaces simbólicos (necesario para compilación), el mecanismo `chroot` impide que las referencias relativas escapen de la raíz virtual. El atacante solo ve los archivos "falsos" del entorno aislado.

---

## 2. Prueba de Integridad del Sistema (Sabotaje)

**Objetivo:** Verificar que el código ejecutado no puede reiniciar el servidor, detener procesos críticos o provocar pánicos en el Kernel.

**Vector de Ataque (Python):**

```python
os.system("shutdown now")       # Intento de apagado
os.kill(1, signal.SIGKILL)      # Intento de matar PID 1
open("/proc/sysrq-trigger", "w").write("b") # Intento de Kernel Panic
```

### 2.1. Resultados en Piston

```text
Shutdown Exit Code: 32512 (Command not found)
KILL BLOCKED: [Errno 1] Operation not permitted
SYSRQ BLOCKED: [Errno 30] Read-only file system
```

### 2.2. Resultados en Judge0

```text
Ret: 32512
Blocked: [Errno 1] Operation not permitted
Blocked: [Errno 30] Read-only file system
```

**Conclusión:** ✅ **SEGURO.** Ambos motores carecen de _Capabilities_ administrativas (`CAP_SYS_BOOT`, `CAP_KILL`). El sistema operativo host es inmune a comandos destructivos desde el sandbox.

---

## 3. Validación de Aislamiento de Red

**Objetivo:** Determinar si los contenedores pueden establecer conexiones salientes (Internet) o escanear la red local.

**Vector de Prueba:**

```python
socket.create_connection(("google.com", 80), timeout=2)
```

### 3.1. Judge0 (Modo Estricto)

**Resultado Observado:**

```text
BLOQUEADO: [Errno -3] Temporary failure in name resolution
```

**Análisis:** 🔒 **AISLADO.**
Judge0 deshabilita la interfaz de red dentro del sandbox (`enable_network=false`). Esto previene la exfiltración de datos, la descarga de malware y ataques laterales a la red interna.

### 3.2. Piston (Modo Flexible)

**Resultado Observado:**

```text
CONECTADO! (PELIGRO: Hay internet)
```

**Análisis:** 🔓 **PERMISIVO.**
Piston permite tráfico de salida por defecto (NAT).

- **Justificación:** Piston está diseñado para permitir la instalación de paquetes en tiempo de ejecución en ciertos lenguajes.
- **Nota de Seguridad:** En un entorno de producción estricto, se recomienda aplicar una **Kubernetes NetworkPolicy** de tipo `Deny-All Egress` al namespace `home-services` para bloquear este tráfico si no es deseado.

---

## 4. Matriz Final de Seguridad

| Vector de Seguridad | Judge0 (Evaluador)    | Piston (Ejecutor)           | Estado Global   |
| :------------------ | :-------------------- | :-------------------------- | :-------------- |
| **Aislamiento FS**  | ✅ Hermético (Chroot) | ✅ Hermético (Chroot)       | **Seguro**      |
| **Protección CPU**  | ✅ Time Limit         | ✅ Time Limit               | **Seguro**      |
| **Protección RAM**  | ⚠️ K8s OOM Kill       | ⚠️ K8s OOM Kill             | **Mitigado**    |
| **Integridad Host** | ✅ Inmune             | ✅ Inmune                   | **Seguro**      |
| **Acceso Red**      | 🔒 **Bloqueado**      | 🔓 **Abierto** (Por diseño) | **Documentado** |

**Conclusión del Auditor:**
La infraestructura desplegada cumple con los requisitos de seguridad para un entorno académico. Judge0 ofrece un entorno de "Zero Trust" ideal para exámenes, mientras que Piston ofrece flexibilidad para ejecución general.
