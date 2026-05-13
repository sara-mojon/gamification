# 🏆 Ofelia Code - Gamificación para Desarrolladores

![Status](https://img.shields.io/badge/Status-En%20Desarrollo-brightgreen)
![React](https://img.shields.io/badge/Frontend-React%20%7C%20TypeScript-blue)
![Spring Boot](https://img.shields.io/badge/Backend-Spring%20Boot%20%7C%20Java-success)
![Microservices](https://img.shields.io/badge/Architecture-Microservices-orange)
![Kubernetes](https://img.shields.io/badge/Deployment-Kubernetes-326CE5)

Ofelia Code es un sistema interactivo diseñado para convertir el aprendizaje y la práctica de programación en una experiencia divertida, competitiva y social. Integrado directamente con Slack, permite a los equipos de desarrollo resolver retos de código, retarse a duelos y escalar en un ranking global.

## ✨ Funcionalidades Principales

- 🤖 **Integración con Slack:** Lanza retos, pide pistas o reta a tus compañeros sin salir de tu herramienta de comunicación diaria.
- 💻 **IDE Integrado:** Editor de código en el navegador impulsado por Monaco Editor (el motor de VS Code).
- 🚀 **Ejecución de Código Aislada:** Evaluación de código en múltiples lenguajes (JavaScript, Python, Java, C) de forma segura a través de Judge0.
- ⚔️ **Duelos 1vs1:** Reta a un compañero usando `/duel @usuario`. El primero en pasar todos los tests de un reto aleatorio gana, y el bot anuncia la victoria públicamente.
- 🏆 **Sistema de Progresión:** Gana puntos (px), mejora tu rango y compite por el primer puesto en la Clasificación Global.
- 🔐 **Seguridad Avanzada:** Autenticación y gestión de usuarios delegada mediante Keycloak.

## 🛠️ Stack Tecnológico

**Frontend:**

- React 18 + TypeScript + Vite
- React Router DOM
- Autenticación: `react-oidc-context` (Keycloak)
- Editor: `@monaco-editor/react`
- UI/UX: Lucide React, SweetAlert2 / react-confetti

**Backend (Arquitectura de Microservicios):**

- Java 17+ / Spring Boot 3
- `service-users`: Gestión de perfiles, estadísticas de usuarios y comunicación con el frontend.
- `service-challenges`: Gestión de retos, validación en Judge0 y lógica asíncrona de duelos.
- `service-gamification`: Motor encargado del cálculo de puntuación, experiencia (px) y progresión de rangos.
- Spring Data JPA & Hibernate
- Control de versiones de BBDD: **Flyway**

**Infraestructura & Integraciones:**

- **Kubernetes (K8s)** para la orquestación y despliegue de todos los servicios.
- Docker & contenedores
- Slack API (Bolt / Webhooks)
- Judge0 (Motor de ejecución de código)
- Keycloak (Identity and Access Management)
- PostgreSQL

## 📱 Comandos de Slack Disponibles

- `/challenge` - Recibe un reto aleatorio para resolver.
- `/duel @usuario` - Lanza un guante a un compañero para un duelo de código.
- `/rank` - Comprueba la clasificación global del equipo.
- `/hint` - Pide una pista si te quedas atascado en un reto.
- `/info` - Muestra la información de la plataforma y el enlace de acceso.

## 🚀 Despliegue e Instalación

El proyecto está diseñado para ejecutarse en un entorno orquestado con Kubernetes, asegurando alta disponibilidad y escalabilidad de sus microservicios.

### Requisitos Previos

- Node.js (v18 o superior) & Java 17+ (para desarrollo local)
- Docker
- Clúster de Kubernetes (Minikube, k3s, o Docker Desktop con K8s habilitado)
- `kubectl` (CLI de Kubernetes)
- Maven

### Pasos para el Despliegue en Kubernetes ☸️

**1. Clonar el repositorio**

```bash
git clone [https://github.com/](https://github.com/)[tu-usuario]/[tu-repo].git
cd [tu-repo]
```

**2. Configurar Variables de Entorno (Secrets y ConfigMaps)**
Asegúrate de configurar los manifiestos de Kubernetes para inyectar las credenciales necesarias (Token de Slack, URLs de Keycloak, credenciales de PostgreSQL).

**3. Desplegar la Infraestructura Base**
Aplica los manifiestos para levantar las bases de datos, Judge0 y Keycloak:

```bash
kubectl apply -f k8s/infra/
```

**4. Desplegar los Microservicios (Backend)**
Despliega los servicios de usuarios, retos y gamificación:

```bash
kubectl apply -f k8s/apps/service-users.yaml
kubectl apply -f k8s/apps/service-challenges.yaml
kubectl apply -f k8s/apps/service-gamification.yaml
```

**5. Desplegar el Frontend**
Finalmente, despliega la aplicación de React:

```bash
kubectl apply -f k8s/frontend/
```
