# Sistema de Gestión de Emergencias Médicas
> **EA2 – Concurrencia y Programación Multihilo en Java**  
> **Institución Universitaria Digital de Antioquia (IUDigital)**

Simulación concurrente de un centro de emergencias médicas que gestiona llamadas simultáneas, prioriza incidentes por gravedad, tiempo de espera y distancia, y administra recursos compartidos (ambulancias y tripulaciones médicas) garantizando thread-safety, prevención de deadlocks y tolerancia a fallos.

---

## Objetivo de la Actividad
Aplicar conceptos avanzados de concurrencia y programación multihilo en Java para desarrollar un sistema que gestione recursos compartidos en tiempo real bajo condiciones de alta presión, siguiendo el caso de estudio del Centro de Emergencias Médicas.

---

## Requisitos del Entorno

| Herramienta | Versión recomendada |
|-------------|---------------------|
| Java JDK    | 17+ (probado y validado en Java 21 LTS) |
| Apache Maven| 3.8+                |
| Consola     | PowerShell, CMD, Windows Terminal o Bash |

---

## Instrucciones de Compilación y Ejecución

### Opción 1: Con Maven (Recomendada)
1. **Compilar y empaquetar el proyecto:**
   ```bash
   mvn clean package
   ```
   Esto generará el archivo `sistema-emergencias.jar` en la carpeta `target/`.

2. **Ejecutar el sistema:**
   ```bash
   java -jar target/sistema-emergencias.jar
   ```
   *(También se encuentra una copia directa en la raíz: `java -jar sistema-emergencias.jar`)*

3. **Ejecutar con carga personalizada (número de emergencias):**
   ```bash
   # Simular 10, 20, 50 o 100 emergencias
   java -jar target/sistema-emergencias.jar 50
   ```

### Opción 2: Script automatizado en Windows
```cmd
compilar_y_ejecutar.bat
```
O pasando el número de emergencias:
```cmd
compilar_y_ejecutar.bat 10
```

---

## Estructura Real del Proyecto

```
sa/
├── pom.xml                                # Configuración de Maven (Java 21, maven-jar-plugin)
├── compilar_y_ejecutar.bat               # Script batch de compilación y ejecución
├── compilar_y_ejecutar.sh                # Script bash para entornos Linux/macOS
├── README.md                             # Documentación de usuario y ejecución
└── src/main/java/com/emergencias/
    ├── Main.java                         # Punto de entrada, configuración y orquestación
    │
    ├── model/                            # Modelos de dominio del problema
    │   ├── Emergency.java                # Emergencia (Comparable, prioridad dinámica, CAS de estado)
    │   ├── Ambulance.java                # Ambulancia (recurso compartido con reserva CAS)
    │   ├── Severity.java                 # Enum de gravedad (CRÍTICO, GRAVE, MODERADO, LEVE)
    │   ├── EmergencyStatus.java          # Ciclo de vida (PENDIENTE, ASIGNADO, EN CURSO, COMPLETADO, CANCELADO)
    │   ├── ResourceStatus.java           # Estados del recurso (DISPONIBLE, EN CAMINO, OCUPADO, MANTENIMIENTO)
    │   ├── Location.java                 # Coordenadas geográficas y cálculo euclidiano de distancia
    │   ├── MessageType.java              # Tipos de eventos para el bus
    │   └── SystemMessage.java            # Registro inmutable de eventos entre subsistemas
    │
    ├── concurrent/                       # Estructuras thread-safe y gestión de recursos
    │   ├── EmergencyQueue.java           # Cola de prioridad respaldada por PriorityBlockingQueue
    │   └── AmbulancePool.java            # Pool de ambulancias protegido con ReentrantReadWriteLock y CAS
    │
    ├── communication/                    # Desacoplamiento de componentes (Observer / Message Bus)
    │   ├── CommunicationBus.java         # Bus singleton con ConcurrentHashMap y CopyOnWriteArrayList
    │   └── MessageListener.java          # Interfaz funcional suscriptora de eventos
    │
    ├── service/                          # Hilos de procesamiento y lógica concurrente
    │   ├── EmergencySimulator.java       # Hilo productor de llamadas imprevistas
    │   ├── EmergencyOperator.java        # Hilos validadores de llamadas (Producer-Consumer)
    │   ├── Dispatcher.java               # Hilos despachadores que asignan recursos óptimos
    │   └── AmbulanceResponseThread.java  # Hilos de ciclo de atención y traslado al hospital
    │
    └── monitor/                          # Subsistema de observabilidad en tiempo real
        └── MonitoringSystem.java         # Hilo demonio con prioridad mínima que renderiza el tablero
```

> **Aclaración sobre nombres del dominio:**  
> Cada recurso de ambulancia (`Ambulance`) incluye a su tripulación médica asignada (`crewName`), modelando en conjunto el personal y equipamiento dentro de la unidad de respuesta médica. El pool thread-safe que los administra se denomina `AmbulancePool`.

---

## Arquitectura y Patrones de Concurrencia

```
                       ┌──────────────────────┐
       Llamadas ─────► │  EmergencySimulator   │ (Hilo demonio productor)
      imprevistas      └──────────┬───────────┘
                                  │ rawCallQueue (EmergencyQueue)
                       ┌──────────▼───────────┐
                       │  EmergencyOperator   │ (Pool de N hilos validadores)
                       └──────────┬───────────┘
                                  │ priorityQueue (PriorityBlockingQueue con orden dinámico)
                       ┌──────────▼───────────┐
                       │      Dispatcher      │ (Pool de M hilos despachadores)
                       └──────────┬───────────┘
                                  │ Reserva atómica CAS / AmbulancePool
                       ┌──────────▼───────────┐
                       │ AmbulanceResponse    │ (Hilo efímero por cada servicio activo)
                       └──────────────────────┘

       MonitoringSystem ──────────────────────► Lee snapshots sin bloquear despachos
       CommunicationBus ──────────────────────► Event Bus (Observer) desacoplado
```

### Patrones Aplicados:
1. **Producer-Consumer:**
   - `Simulator` produce en `rawCallQueue`.
   - `EmergencyOperator` consume, valida y produce en `priorityQueue`.
   - `Dispatcher` consume de `priorityQueue` y coordina con `AmbulancePool`.
2. **Observer / Event-Bus:**
   - Implementado en `CommunicationBus` para notificar a hospitales, operadores y monitor sin dependencias rígidas.
3. **Resource Pool / Object Pool:**
   - `AmbulancePool` gestiona unidades disponibles y ocupadas.
4. **Compare-And-Swap (Lock-Free Resource Acquisition):**
   - `Ambulance.tryAcquire()` usa `AtomicReference.compareAndSet` para asegurar que solo un despachador capture cada unidad.
5. **Thread-per-Task:**
   - `AmbulanceResponseThread` ejecuta de forma independiente el desplazamiento y atención de cada paciente.

---

## Mecanismos de Sincronización y Prevención de Problemas

| Mecanismo Técnico | Dónde se usa | Problema de concurrencia que previene |
|-------------------|--------------|---------------------------------------|
| `PriorityBlockingQueue` | `EmergencyQueue` | Previene race conditions en el encolamiento; despacha según prioridad dinámica. |
| `AtomicReference.compareAndSet()` | `Ambulance.tryAcquire()` | **Race Condition:** Evita que dos despachadores asignen la misma ambulancia a la vez. |
| `AtomicReference.compareAndSet()` | `Emergency.transitionStatus()` | **Doble procesamiento:** Garantiza que un caso solo sea tomado por un despachador. |
| `ReentrantReadWriteLock` | `AmbulancePool` | Permite múltiples lecturas simultáneas de disponibilidad con exclusión limpia. |
| `ConcurrentHashMap` | `MonitoringSystem`, `CommunicationBus` | Acceso concurrente a mapas sin cuellos de botella ni `ConcurrentModificationException`. |
| `CopyOnWriteArrayList` | `CommunicationBus.subscribers` | Notificaciones thread-safe mientras se agregan oyentes dinámicamente. |
| Prioridad dinámica por fórmula | `Emergency.computePriority()` | **Starvation (inanición):** Casos leves no esperan indefinidamente al sumar tiempo acumulado. |
| Re-enqueue a la cola prioritaria | `Dispatcher.dispatch()` | **Tolerancia a fallos:** Si no hay unidades libres, la emergencia no se pierde ni se duplica. |
| Ausencia de locks anidados | Arquitectura general | **Deadlock:** No se solicitan locks encadenados entre hilos. |

---

## Demostración en Video (Puntos Clave para la Exposición)

Al grabar el video explicativo de 10–15 minutos, se recomienda demostrar:
1. **Inicio de la simulación:** Explicar el inicio de los hilos de operadores, despachadores y monitor.
2. **Priorización dinámica en acción:** Mostrar cómo un caso `CRÍTICO` pasa inmediatamente al frente de casos `MODERADO` o `LEVE`.
3. **Escenario de contención y tolerancia a falta de recursos:**
   - Ejecutar con más emergencias que ambulancias (ej. `java -jar sistema-emergencias.jar 50`).
   - Mostrar el mensaje en amarillo de reencolamiento de emergencia cuando las 6 ambulancias están ocupadas.
   - Demostrar que ninguna emergencia se descarta ni se duplica.
4. **Monitoreo en tiempo real:** Mostrar el tablero que se refresca en segundo plano reflejando las estadísticas y estados de cada ambulancia.

---

## Integrantes del Equipo

| Nombre Completo | Documento / ID | Modalidad | Contribución Principal |
|-----------------|----------------|-----------|------------------------|
| **Alexandra Gómez** | [Tu Documento / ID] | **Individual** | Desarrollo completo: arquitectura multihilo, concurrencia (CAS, colas y locks), simulación, monitoreo y documentación técnica. |
