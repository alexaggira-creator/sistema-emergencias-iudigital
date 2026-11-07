package com.emergencias.concurrent;

import com.emergencias.model.Ambulance;
import com.emergencias.model.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Pool thread-safe de ambulancias del sistema.
 *
 * <p>Utiliza un {@link ReentrantReadWriteLock} para maximizar el rendimiento:
 * múltiples hilos pueden <em>consultar</em> el estado simultáneamente,
 * mientras que las operaciones de <em>asignación/liberación</em> requieren
 * acceso exclusivo.</p>
 *
 * <p>El método {@link #findNearest(Location)} aplica el algoritmo de
 * priorización considerando distancia geográfica y disponibilidad.</p>
 */
public class AmbulancePool {

    private final List<Ambulance> ambulances;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public AmbulancePool(List<Ambulance> ambulances) {
        this.ambulances = new ArrayList<>(ambulances);
    }

    // ------------------------------------------------------------------ //
    //  Búsqueda de la ambulancia más cercana disponible                   //
    // ------------------------------------------------------------------ //

    /**
     * Encuentra la ambulancia disponible más cercana a la ubicación dada
     * y la reserva de forma atómica.
     *
     * @param target ubicación de la emergencia
     * @return la ambulancia reservada, o {@link Optional#empty()} si no hay
     */
    public Optional<Ambulance> findNearest(Location target) {
        lock.readLock().lock();
        try {
            return ambulances.stream()
                    .filter(a -> a.getStatus() == com.emergencias.model.ResourceStatus.AVAILABLE)
                    .min((a, b) -> Double.compare(
                            a.getCurrentLocation().distanceTo(target),
                            b.getCurrentLocation().distanceTo(target)))
                    .filter(Ambulance::tryAcquire);  // CAS: solo uno gana la carrera
        } finally {
            lock.readLock().unlock();
        }
    }

    // ------------------------------------------------------------------ //
    //  Consulta del estado (solo lectura)                                 //
    // ------------------------------------------------------------------ //

    public List<Ambulance> getAll() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(ambulances);
        } finally {
            lock.readLock().unlock();
        }
    }

    public long availableCount() {
        lock.readLock().lock();
        try {
            return ambulances.stream()
                    .filter(a -> a.getStatus() == com.emergencias.model.ResourceStatus.AVAILABLE)
                    .count();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Ambulance> getByStatus(com.emergencias.model.ResourceStatus status) {
        lock.readLock().lock();
        try {
            return ambulances.stream()
                    .filter(a -> a.getStatus() == status)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }
}

