package com.emergencias;

import com.emergencias.communication.CommunicationBus;
import com.emergencias.concurrent.AmbulancePool;
import com.emergencias.concurrent.EmergencyQueue;
import com.emergencias.model.*;
import com.emergencias.monitor.MonitoringSystem;
import com.emergencias.service.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Punto de entrada del Sistema de Gestión de Emergencias Médicas.
 *
 * <h2>Arquitectura de concurrencia</h2>
 * 
 * <pre>
 *                   ┌──────────────────────┐
 *   Emergencias ──► │  EmergencySimulator   │  (1 hilo demonio)
 *   aleatorias      └────────┬─────────────┘
 *                            │ rawCallQueue (EmergencyQueue / PBQ de llamadas crudas)
 *                   ┌────────▼─────────────┐
 *                   │  EmergencyOperator   │  (N hilos demonio)
 *                   │  (valida, 200-500ms) │
 *                   └────────┬─────────────┘
 *                            │ priorityQueue (EmergencyQueue / PBQ con prioridad real)
 *                   ┌────────▼─────────────┐
 *                   │      Dispatcher      │  (M hilos demonio)
 *                   │  (consume por PBQ)   │
 *                   └────────┬─────────────┘
 *                            │ CAS / AmbulancePool
 *                   ┌────────▼─────────────┐
 *                   │ AmbulanceResponse    │  (hilo por despacho)
 *                   └──────────────────────┘
 *
 *   MonitoringSystem ──────────────────────► (1 hilo demonio, prioridad MIN)
 *   CommunicationBus ──────────────────────► (Singleton, sin hilo propio)
 * </pre>
 *
 * <h2>Mecanismos de sincronización</h2>
 * <ul>
 * <li>{@code PriorityBlockingQueue} – cola thread-safe con prioridad
 * dinámica</li>
 * <li>{@code AtomicReference} + CAS – reserva atómica de ambulancias</li>
 * <li>{@code ReentrantReadWriteLock} – lectura concurrente del pool de
 * ambulancias</li>
 * <li>{@code ConcurrentHashMap} – estado compartido del monitor</li>
 * <li>{@code CopyOnWriteArrayList} – lista de suscriptores del bus</li>
 * <li>{@code AtomicBoolean} – señal de parada del sistema</li>
 * </ul>
 */
public class Main {

    // ------------------------------------------------------------------ //
    // Configuración de la simulación //
    // Modifica estos valores para probar distintas cargas //
    // ------------------------------------------------------------------ //
    private static final int NUM_AMBULANCES = 6;
    private static final int NUM_OPERATORS = 3;
    private static final int NUM_DISPATCHERS = 2;
    private static final int TOTAL_EMERGENCIES = 20;
    private static final int SIMULATION_TIMEOUT_S = 300;

    public static void main(String[] args) throws InterruptedException {

        // Permitir override de TOTAL_EMERGENCIES vía argumento CLI
        int totalEmergencies = TOTAL_EMERGENCIES;
        if (args.length > 0) {
            try {
                totalEmergencies = Integer.parseInt(args[0]);
                System.out.printf("[CONFIG] Emergencias configuradas desde argumento: %d%n", totalEmergencies);
            } catch (NumberFormatException ignored) {
                System.out.println("[CONFIG] Argumento inválido; usando valor por defecto: " + TOTAL_EMERGENCIES);
            }
        }

        printBanner(totalEmergencies);

        // ---------------------------------------------------------------- //
        // 1. Crear recursos //
        // ---------------------------------------------------------------- //
        List<Ambulance> ambulances = createAmbulances(NUM_AMBULANCES);
        AmbulancePool ambulancePool = new AmbulancePool(ambulances);

        // ---------------------------------------------------------------- //
        // 2. Crear colas concurrentes //
        // //
        // rawCallQueue : PBQ que recibe llamadas crudas del simulador //
        // Los operadores consumen de aquí //
        // priorityQueue : PBQ de la que consumen los despachadores //
        // Los operadores producen aquí DESPUÉS de //
        // validar → el Dispatcher ve el orden correcto //
        // ---------------------------------------------------------------- //
        EmergencyQueue rawCallQueue = new EmergencyQueue(); // Simulador → Operador
        EmergencyQueue priorityQueue = new EmergencyQueue(); // Operador → Dispatcher

        // ---------------------------------------------------------------- //
        // 3. Señal de control del sistema //
        // ---------------------------------------------------------------- //
        AtomicBoolean running = new AtomicBoolean(true);

        // ---------------------------------------------------------------- //
        // 4. Sistema de monitoreo //
        // ---------------------------------------------------------------- //
        MonitoringSystem monitor = new MonitoringSystem(priorityQueue, ambulancePool, running);

        // ---------------------------------------------------------------- //
        // 5. Configurar bus de comunicación (Patrón Observer) //
        // ---------------------------------------------------------------- //
        CommunicationBus bus = CommunicationBus.getInstance();
        bus.subscribe("ALL", msg -> monitor.logEvent(msg.toString()));

        // ---------------------------------------------------------------- //
        // 6. Lanzar hilos //
        // ---------------------------------------------------------------- //

        // Monitoreo (daemon, prioridad MIN)
        monitor.start();

        // Simulador de emergencias
        EmergencySimulator simulator = new EmergencySimulator(rawCallQueue, running, totalEmergencies);
        simulator.setMonitor(monitor);
        simulator.start();

        // Operadores: validan y pasan a la cola prioritaria
        List<EmergencyOperator> operators = new ArrayList<>();
        for (int i = 1; i <= NUM_OPERATORS; i++) {
            EmergencyOperator op = new EmergencyOperator(
                    String.valueOf(i), rawCallQueue, priorityQueue, running);
            operators.add(op);
            op.start();
        }

        // Despachadores: consumen de la cola prioritaria (PBQ)
        List<Dispatcher> dispatchers = new ArrayList<>();
        for (int i = 1; i <= NUM_DISPATCHERS; i++) {
            Dispatcher d = new Dispatcher(
                    String.valueOf(i), priorityQueue, ambulancePool, running);
            dispatchers.add(d);
            d.start();
        }

        // ---------------------------------------------------------------- //
        // 7. Esperar finalización de la simulación //
        // ---------------------------------------------------------------- //
        // Esperar a que el simulador termine de generar llamadas
        simulator.join(SIMULATION_TIMEOUT_S * 1000L);

        // Esperar a que los operadores terminen de validar todo el backlog crudo y finalicen sus tareas activas
        while (!rawCallQueue.isEmpty() || operators.stream().anyMatch(EmergencyOperator::isActive)) {
            Thread.sleep(300);
        }

        // Una vez que no hay llamadas en validación, esperamos a que la cola prioritaria se vacíe por completo
        while (!priorityQueue.isEmpty()) {
            Thread.sleep(500);
        }

        // Señalizar a los operadores y despachadores que terminen
        running.set(false);

        // Esperar a que procesen el backlog restante
        for (EmergencyOperator op : operators) {
            op.join(10_000);
        }
        for (Dispatcher d : dispatchers) {
            d.join(15_000);
        }

        // Esperar a que todas las ambulancias en curso terminen
        waitForActiveAmbulances(ambulances);

        // Pausa final para que el monitor imprima el estado definitivo
        Thread.sleep(2_500);

        // ---------------------------------------------------------------- //
        // 8. Resumen final //
        // ---------------------------------------------------------------- //
        printFinalSummary(operators, dispatchers, ambulances);

        System.out.println("\n\u001B[1;32m✔ Simulación finalizada.\u001B[0m");
    }

    // ------------------------------------------------------------------ //
    // Helpers //
    // ------------------------------------------------------------------ //

    private static List<Ambulance> createAmbulances(int count) {
        String[] crews = {
                "Equipo Alpha", "Equipo Beta", "Equipo Gamma",
                "Equipo Delta", "Equipo Epsilon", "Equipo Zeta",
                "Equipo Eta", "Equipo Theta"
        };
        List<Ambulance> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            double x = 15 + Math.random() * 70;
            double y = 15 + Math.random() * 70;
            Location base = new Location(x, y, "Base " + i);
            list.add(new Ambulance(String.valueOf(i), crews[i - 1], base));
        }
        return list;
    }

    private static void waitForActiveAmbulances(List<Ambulance> ambulances) throws InterruptedException {
        int maxWaitIterations = 120; // Hasta 2 minutos para asegurar que todas las unidades concluyan su atención
        for (int i = 0; i < maxWaitIterations; i++) {
            boolean allFree = ambulances.stream()
                    .allMatch(a -> a.getStatus() == ResourceStatus.AVAILABLE);
            if (allFree)
                break;
            Thread.sleep(1000);
        }
    }

    private static void printBanner(int totalEmergencies) {
        System.out.println("\u001B[1;31m");
        System.out.println("  ╔══════════════════════════════════════════════════════╗");
        System.out.println("  ║   🚑  SISTEMA DE GESTIÓN DE EMERGENCIAS MÉDICAS     ║");
        System.out.println("  ║        IUDigital de Antioquia  –  EA2  2024         ║");
        System.out.println("  ╚══════════════════════════════════════════════════════╝");
        System.out.println("\u001B[0m");
        System.out.printf("  Ambulancias: %d  |  Operadores: %d  |  Despachadores: %d  |  Emergencias: %d%n%n",
                NUM_AMBULANCES, NUM_OPERATORS, NUM_DISPATCHERS, totalEmergencies);
        System.out.printf("  Tip: ejecutar con argumento numérico para cambiar la carga:%n");
        System.out.printf("       java -jar sistema-emergencias.jar 50%n%n");
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
        }
    }

    private static void printFinalSummary(List<EmergencyOperator> operators,
            List<Dispatcher> dispatchers,
            List<Ambulance> ambulances) {
        System.out.println("\n\u001B[1;37m══════════════════ RESUMEN FINAL ══════════════════\u001B[0m");
        System.out.println("OPERADORES:");
        operators.forEach(op -> System.out.printf("  Operador %-3s → %d emergencias validadas%n",
                op.getOperatorId(), op.getProcessedCount()));
        System.out.println("DESPACHADORES:");
        dispatchers.forEach(d -> System.out.printf("  Despachador %-3s → %d emergencias despachadas%n",
                d.getDispatcherId(), d.getDispatchedCount()));
        System.out.println("AMBULANCIAS:");
        ambulances.forEach(a -> System.out.printf("  AMB-%-4s | %s | Última posición: %s%n",
                a.getId(), a.getStatus(), a.getCurrentLocation()));
    }
}
