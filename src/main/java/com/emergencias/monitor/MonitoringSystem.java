package com.emergencias.monitor;

import com.emergencias.concurrent.AmbulancePool;
import com.emergencias.concurrent.EmergencyQueue;
import com.emergencias.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sistema de monitoreo en tiempo real del centro de emergencias.
 *
 * <p>
 * Se ejecuta como hilo demonio con prioridad reducida para no interferir
 * con el procesamiento de emergencias. Cada ciclo limpia la consola y dibuja
 * un tablero actualizado con:
 * </p>
 * <ul>
 * <li>Estadísticas globales</li>
 * <li>Emergencias pendientes ordenadas por prioridad</li>
 * <li>Estado de las ambulancias</li>
 * <li>Últimos mensajes del bus</li>
 * </ul>
 *
 * <p>
 * Mantiene un registro de todas las emergencias vistas para calcular
 * estadísticas acumuladas (tiempo de respuesta promedio, conteo por gravedad).
 * </p>
 */
public class MonitoringSystem extends Thread {

    private static final int REFRESH_INTERVAL_MS = 2_000;
    private static final int MAX_LOG_LINES = 8;

    // ---------------------------------------------------------------- //
    // Estado rastreado //
    // ---------------------------------------------------------------- //
    private final Map<Integer, Emergency> allEmergencies = new ConcurrentHashMap<>();
    private final List<String> eventLog = new CopyOnWriteArrayList<>();

    // AtomicLong para estadísticas acumuladas thread-safe
    private final AtomicLong totalCompleted = new AtomicLong(0);
    private final AtomicLong totalCancelled = new AtomicLong(0);
    private final AtomicLong sumResponseMs = new AtomicLong(0);
    private final AtomicLong countWithTime = new AtomicLong(0);

    // ---------------------------------------------------------------- //
    // Dependencias //
    // ---------------------------------------------------------------- //
    private final EmergencyQueue emergencyQueue;
    private final AmbulancePool ambulancePool;
    private final AtomicBoolean running;

    public MonitoringSystem(EmergencyQueue emergencyQueue,
            AmbulancePool ambulancePool,
            AtomicBoolean running) {
        super("MonitoringSystem");
        setDaemon(true);
        setPriority(Thread.MIN_PRIORITY);
        this.emergencyQueue = emergencyQueue;
        this.ambulancePool = ambulancePool;
        this.running = running;
    }

    // ---------------------------------------------------------------- //
    // Registro de emergencias //
    // ---------------------------------------------------------------- //

    public void register(Emergency emergency) {
        allEmergencies.put(emergency.getId(), emergency);
    }

    public void logEvent(String event) {
        eventLog.add(0, event);
        // CopyOnWriteArrayList no soporta subList().clear(); usar removeIf o recrear
        // Limitamos el tamaño removiendo elementos del final si supera el máximo
        int maxSize = MAX_LOG_LINES * 3;
        while (eventLog.size() > maxSize) {
            eventLog.remove(eventLog.size() - 1);
        }
    }

    // ---------------------------------------------------------------- //
    // Hilo principal //
    // ---------------------------------------------------------------- //

    @Override
    public void run() {
        while (running.get()) {
            try {
                Thread.sleep(REFRESH_INTERVAL_MS);
                updateStats();
                printDashboard();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Impresión final
        printDashboard();
    }

    // ---------------------------------------------------------------- //
    // Estadísticas //
    // ---------------------------------------------------------------- //

    private void updateStats() {
        // En printStats se calculan y presentan las métricas en tiempo real
        // directamente sobre el ConcurrentHashMap thread-safe allEmergencies.
    }

    // ---------------------------------------------------------------- //
    // Renderizado del tablero //
    // ---------------------------------------------------------------- //

    private void printDashboard() {
        // ANSI clear screen
        System.out.print("\033[2J\033[H");

        String line = "═".repeat(70);
        String thin = "─".repeat(70);

        System.out.println("\u001B[1;37m" + line);
        System.out.println("   🚑  CENTRO DE EMERGENCIAS MÉDICAS  –  TABLERO EN TIEMPO REAL");
        System.out.println(line + "\u001B[0m");

        printStats();
        System.out.println("\u001B[90m" + thin + "\u001B[0m");
        printPendingEmergencies();
        System.out.println("\u001B[90m" + thin + "\u001B[0m");
        printAmbulanceStatus();
        System.out.println("\u001B[90m" + thin + "\u001B[0m");
        printEventLog();
        System.out.println("\u001B[90m" + thin + "\u001B[0m");
    }

    private void printStats() {
        long pending = allEmergencies.values().stream()
                .filter(e -> e.getStatus() == EmergencyStatus.PENDING).count();
        long assigned = allEmergencies.values().stream()
                .filter(e -> e.getStatus() == EmergencyStatus.ASSIGNED).count();
        long inProgress = allEmergencies.values().stream()
                .filter(e -> e.getStatus() == EmergencyStatus.IN_PROGRESS).count();
        long completed = allEmergencies.values().stream()
                .filter(e -> e.getStatus() == EmergencyStatus.COMPLETED).count();
        long critical = allEmergencies.values().stream()
                .filter(e -> e.getSeverity() == Severity.CRITICAL).count();

        // Tiempo promedio de respuesta
        double avgResponse = allEmergencies.values().stream()
                .filter(e -> e.getAttendedAt() != null)
                .mapToLong(e -> Duration.between(e.getReceivedAt(), e.getAttendedAt()).toMillis())
                .average().orElse(0);

        long availAmb = ambulancePool.availableCount();

        System.out.printf("  \u001B[1mTotal recibidas:\u001B[0m %-5d  " +
                "\u001B[33mPendientes:\u001B[0m %-5d  " +
                "\u001B[34mAsignadas:\u001B[0m %-5d  " +
                "\u001B[36mEn curso:\u001B[0m %-5d%n",
                allEmergencies.size(), pending, assigned, inProgress);
        System.out.printf("  \u001B[32mCompletadas:\u001B[0m %-5d  " +
                "\u001B[31mCríticas:\u001B[0m %-5d  " +
                "\u001B[32mAmbulancias libres:\u001B[0m %-3d  " +
                "\u001B[36mT. resp. prom.:\u001B[0m %.1f s%n",
                completed, critical, availAmb, avgResponse / 1000.0);
    }

    private void printPendingEmergencies() {
        List<Emergency> allPending = allEmergencies.values().stream()
                .filter(e -> e.getStatus() == EmergencyStatus.PENDING)
                .sorted()
                .toList();

        System.out.printf("  \u001B[1;33m▶ EMERGENCIAS PENDIENTES (Total: %d | Mostrando hasta 10 por prioridad)\u001B[0m%n",
                allPending.size());

        if (allPending.isEmpty()) {
            System.out.println("    \u001B[32m(ninguna pendiente)\u001B[0m");
            return;
        }

        List<Emergency> topList = allPending.stream().limit(10).toList();
        for (Emergency e : topList) {
            long waitSec = Duration.between(e.getReceivedAt(), Instant.now()).toSeconds();
            System.out.printf("    EM-%03d | %-10s | Espera: %3ds | %s%n",
                    e.getId(), e.getSeverity().getLabel(), waitSec, e.getDescription());
        }
    }

    private void printAmbulanceStatus() {
        System.out.println("  \u001B[1;36m▶ ESTADO DE AMBULANCIAS\u001B[0m");
        for (Ambulance amb : ambulancePool.getAll()) {
            String statusColor = switch (amb.getStatus()) {
                case AVAILABLE -> "\u001B[32m";
                case EN_ROUTE -> "\u001B[33m";
                case BUSY -> "\u001B[31m";
                case MAINTENANCE -> "\u001B[90m";
            };
            String assignedInfo = amb.getAssignedEmergencyId() != null
                    ? " → EM-" + String.format("%03d", amb.getAssignedEmergencyId())
                    : "";
            System.out.printf("    AMB-%-4s | %s%-15s\u001B[0m | Tripulación: %s%s%n",
                    amb.getId(), statusColor,
                    amb.getStatus().getLabel(), amb.getCrewName(), assignedInfo);
        }
    }

    private void printEventLog() {
        System.out.println("  \u001B[1;37m▶ ÚLTIMOS EVENTOS\u001B[0m");
        List<String> recent = eventLog.stream().limit(MAX_LOG_LINES).toList();
        if (recent.isEmpty()) {
            System.out.println("    (sin eventos aún)");
            return;
        }
        for (String event : recent) {
            System.out.println("    " + event);
        }
    }
}
