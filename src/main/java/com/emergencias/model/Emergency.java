package com.emergencias.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Representa una emergencia médica en el sistema.
 *
 * <p>Esta clase es <em>thread-safe</em>: el estado y la prioridad se
 * actualizan mediante campos atómicos para evitar condiciones de carrera
 * cuando múltiples hilos (despachadores, monitor) acceden simultáneamente.</p>
 *
 * <p>Implementa {@link Comparable} para ser ordenada correctamente en la
 * {@link java.util.concurrent.PriorityBlockingQueue}.</p>
 */
public class Emergency implements Comparable<Emergency> {

    // ------------------------------------------------------------------ //
    //  Generador de IDs únicos                                            //
    // ------------------------------------------------------------------ //
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(1);

    // ------------------------------------------------------------------ //
    //  Campos inmutables                                                  //
    // ------------------------------------------------------------------ //
    private final int       id;
    private final Severity  severity;
    private final Location  location;
    private final String    description;
    private final Instant   receivedAt;

    // ------------------------------------------------------------------ //
    //  Campos mutables thread-safe                                        //
    // ------------------------------------------------------------------ //
    private final AtomicReference<EmergencyStatus> status;
    private volatile String assignedAmbulanceId;
    private volatile Instant attendedAt;

    // ------------------------------------------------------------------ //
    //  Constructor                                                        //
    // ------------------------------------------------------------------ //
    public Emergency(Severity severity, Location location, String description) {
        this.id          = ID_COUNTER.getAndIncrement();
        this.severity    = severity;
        this.location    = location;
        this.description = description;
        this.receivedAt  = Instant.now();
        this.status      = new AtomicReference<>(EmergencyStatus.PENDING);
    }

    // ------------------------------------------------------------------ //
    //  Lógica de prioridad                                                //
    // ------------------------------------------------------------------ //

    /**
     * Calcula la prioridad dinámica de la emergencia considerando:
     * <ul>
     *   <li>Peso de la gravedad (mayor peso → mayor prioridad)</li>
     *   <li>Tiempo de espera acumulado (segundos desde la recepción)</li>
     * </ul>
     *
     * <p>La fórmula combina ambos factores para evitar inanición de
     * emergencias de baja gravedad que llevan mucho tiempo esperando.</p>
     */
    public double computePriority() {
        long waitSeconds = Instant.now().getEpochSecond() - receivedAt.getEpochSecond();
        return (severity.getWeight() * 1000.0) + (waitSeconds * 0.5);
    }

    /**
     * Intenta cambiar el estado de la emergencia de forma atómica.
     *
     * @param expected estado esperado actual
     * @param next     nuevo estado
     * @return {@code true} si la transición se realizó correctamente
     */
    public boolean transitionStatus(EmergencyStatus expected, EmergencyStatus next) {
        return status.compareAndSet(expected, next);
    }

    /**
     * Fuerza o establece el estado (por ejemplo, en caso de interrupción/cancelación).
     */
    public void setStatus(EmergencyStatus newStatus) {
        status.set(newStatus);
    }

    // ------------------------------------------------------------------ //
    //  Comparable – orden inverso: mayor prioridad va primero             //
    // ------------------------------------------------------------------ //
    @Override
    public int compareTo(Emergency other) {
        return Double.compare(other.computePriority(), this.computePriority());
    }

    // ------------------------------------------------------------------ //
    //  Getters                                                            //
    // ------------------------------------------------------------------ //
    public int              getId()                  { return id; }
    public Severity         getSeverity()            { return severity; }
    public Location         getLocation()            { return location; }
    public String           getDescription()         { return description; }
    public Instant          getReceivedAt()          { return receivedAt; }
    public EmergencyStatus  getStatus()              { return status.get(); }
    public String           getAssignedAmbulanceId() { return assignedAmbulanceId; }
    public Instant          getAttendedAt()          { return attendedAt; }

    // ------------------------------------------------------------------ //
    //  Setters con visibilidad de hilo                                    //
    // ------------------------------------------------------------------ //
    public void setAssignedAmbulanceId(String id) { this.assignedAmbulanceId = id; }
    public void setAttendedAt(Instant ts)         { this.attendedAt = ts; }

    @Override
    public String toString() {
        return String.format("[EM-%03d | %s | %s | %s]",
                id, severity, status.get(), description);
    }
}

