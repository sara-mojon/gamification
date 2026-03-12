<!-- resumen_vulnerabilidades_riesgos.md -->

# Registro Centralizado de Vulnerabilidades y Riesgos Residuales

**Alcance:** Infraestructura de Ejecución (Piston, Judge0, Kubernetes Host).

---

## 1. Introducción

Este documento recoge las limitaciones de seguridad, vulnerabilidades conocidas y riesgos residuales identificados durante la auditoría técnica de la infraestructura. Se clasifican según su severidad y se detalla la estrategia de mitigación o aceptación del riesgo.

## 2. Matriz de Riesgos

| ID          | Componente          | Vulnerabilidad / Riesgo                                                                                                                                                                                            | Severidad | Estado / Mitigación                                                                                                                                                                       |
| :---------- | :------------------ | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :-------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **VULN-01** | **Piston & Judge0** | **Falta de granularidad en límite de RAM (Soft Limit)** <br> Los motores no pueden detener la ejecución _exactamente_ en el umbral de software (ej. 128MB) debido a incompatibilidades con métricas de Cgroups v2. | 🟠 Media  | **Mitigado.** Seguridad delegada a Kubernetes. El Pod tiene un límite duro (Hard Limit). Si se supera, el Kernel invoca al _OOM Killer_. El servicio persiste, el contenedor se reinicia. |
| **VULN-02** | **Piston**          | **Tráfico de Red Saliente (Egress) Permitido** <br> Los contenedores de Piston tienen acceso a Internet (NAT) por defecto, permitiendo conexiones salientes.                                                       | 🟠 Media  | **Mitigado**. Se ha implementado una **Kubernetes NetworkPolicy** de tipo Deny-All Egress en el namespace, bloqueando cualquier conexión saliente no autorizada.                          |
| **VULN-03** | **Piston**          | **Fallo en Telemetría Interna** <br> Los logs reportan `status: XX` y errores de lectura en `memory.events`.                                                                                                       | 🟢 Baja   | **Aceptado.** Error operativo, no de seguridad. Afecta solo a la estadística devuelta, no a la contención del recurso.                                                                    |
| **VULN-04** | **General**         | **Ejecución en Kernel Compartido** <br> Riesgo inherente a la tecnología de contenedores (Docker/LXC) frente a la virtualización completa (VMs). Un exploit de Kernel podría permitir escapar del contenedor.      | 🟢 Baja   | **Riesgo Residual.** Mitigado por actualizaciones constantes de seguridad en el Host (Ubuntu LTS) y perfiles de seguridad por defecto (AppArmor/Seccomp) de K3s.                          |
| **VULN-05** | **Judge0**          | **Error de Codificación en Salida (Non-UTF8)** <br> La API devuelve error si el código ejecutado imprime binarios o caracteres no imprimibles.                                                                     | 🟢 Baja   | **Solucionado.** Se fuerza el uso del parámetro `base64_encoded=true` en el cliente para encapsular la salida y garantizar la integridad del JSON.                                        |
| **VULN-06** | **Piston & Judge0** | **Creación de Enlaces Simbólicos Permitida** <br> Los entornos permiten `symlink` y referencias relativas (`../../`). Un error de configuración en el `chroot` podría permitir lecturas arbitrarias.               | 🟢 Baja   | **Mitigado.** El aislamiento de sistema de archivos (Mount Namespace/Chroot) es robusto. Las referencias `..` se detienen en la raíz de la jaula, impidiendo el acceso al Host real.      |

---

## 3. Conclusión de Auditoría

A pesar de los riesgos identificados, la combinación de medidas de seguridad a nivel de aplicación (Wrappers), sistema operativo (User/Mount Namespaces) y orquestador (Kubernetes Limits) proporciona un entorno de ejecución **SEGURO** y apto para el propósito académico de la plataforma.

## 4. Comparativa de Seguridad: Judge0 vs Piston

Basado en las pruebas de estrés, aislamiento y configuración por defecto, se presenta la siguiente evaluación comparativa:

| Característica de Seguridad | 🛡️ Judge0 (`isolate`)                      | ⚡ Piston (API v2)                                   |
| :-------------------------- | :----------------------------------------- | :--------------------------------------------------- |
| **Filosofía**               | _Zero Trust_ (Todo prohibido por defecto). | _Developer Friendly_ (Permisivo para facilitar uso). |
| **Acceso a Internet**       | 🔒 **Bloqueado** (Nativo).                 | 🔓 **Abierto** (NAT activo por defecto).             |
| **Límite de Archivos (FD)** | 🔒 **Estricto** (~512).                    | 🔓 **Laxo** (~2048+).                                |
| **Aislamiento FS**          | ✅ Chroot estricto.                        | ✅ Chroot estricto.                                  |
| **Escenario Ideal**         | Exámenes, concursos, evaluación ciega.     | IDE Online, Playground, prototipado rápido.          |

**Veredicto Técnico:**

- **Judge0** se considera el motor **más seguro "Out-of-the-Box"** debido a sus políticas restrictivas nativas y menor superficie de ataque (sin red, límites de descriptores más bajos).
- **Piston** es seguro para ejecución controlada, pero requiere capas de seguridad externas adicionales (como _Kubernetes NetworkPolicies_) para igualar el nivel de aislamiento de Judge0 en un entorno de producción hostil.
