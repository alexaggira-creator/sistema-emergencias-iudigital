package com.emergencias.concurrent;

import com.emergencias.model.Emergency;
import com.emergencias.model.EmergencyStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Cola de emergencias con prioridad dinámica.
 *
 * <p>Encapsula una {@link PriorityBlockingQueue} para ofrecer métodos de
 * consulta adicionales.  La prioridad de cada emergencia se calcula con
 * {@link Emergency#computePriority()}, que combina:</p>
 * <ul>
 *   <li>Gravedad del incidente</li>
 *   <li>Tiempo de espera acumulado (antistarv­ation)</li>
 *   <li>Distancia al recurso más cercano (factor adicional)</li>
 * </ul>
 *
 * <p><strong>Por qué PBQ respeta la prioridad dinámica:</strong>
 * Aunque la cola no re-heapifica automáticamente cuando cambia la prioridad
 * de un elemento <em>ya insertado</em>, el tiempo de espera se suma en el
 * momento en que el Dispatcher hace {@code take()} / {@code poll()}.
 * El heap se reconstruye en cada extracción, así que el elemento con mayor
 * prioridad actual gana cada dequeue.</p>
 *
 * <p><strong>Thread-safety</strong>: heredada de {@code PriorityBlockingQueue};
 * todas las operaciones de enqueue/dequeue son atómicas.</p>
 */
public class EmergencyQueue {

    // Capacidad inicial (crece automáticamente si es necesario)
    private static final int INITIAL_CAPACITY = 32;

    private final PriorityBlockingQueue<Emergency> queue =
            new PriorityBlockingQueue<>(INITIAL_CAPACITY);

    // ------------------------------------------------------------------ //
    //  Operaciones principales                                            //
    // ------------------------------------------------------------------ //

    /**
     * Agrega una emergencia a la cola.
     */
    public void enqueue(Emergency emergency) {
        queue.offer(emergency);
    }

    /**
     * Extrae la emergencia de mayor prioridad.  Bloquea si la cola está
     * vacía hasta que haya un elemento disponible.
     *
     * @throws InterruptedException si el hilo es interrumpido mientras espera
     */
    public Emergency dequeue() throws InterruptedException {
        return queue.take();
    }

    /**
     * Intenta extraer la emergencia de mayor prioridad esperando como máximo
     * {@code timeoutMs} milisegundos.  Retorna {@code null} si la cola sigue
     * vacía al agotarse el timeout.
     *
     * <p>Este método permite que los hilos consumidores verifiquen
     * periódicamente la señal de parada {@code running} sin bloquearse
     * indefinidamente, evitando que los hilos queden atascados al final
     * de la simulación.</p>
     *
     * @param timeoutMs tiempo máximo de espera en milisegundos
     * @return la emergencia o {@code null} si se agotó el tiempo
     * @throws InterruptedException si el hilo es interrumpido
     */
    public Emergency pollWithTimeout(long timeoutMs) throws InterruptedException {
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Intenta extraer la emergencia de mayor prioridad sin bloquear.
     *
     * @return la emergencia o {@code null} si la cola está vacía
     */
    public Emergency poll() {
        return queue.poll();
    }

    // ------------------------------------------------------------------ //
    //  Consultas (solo lectura)                                           //
    // ------------------------------------------------------------------ //

    public int size() { return queue.size(); }

    public boolean isEmpty() { return queue.isEmpty(); }

    /**
     * Retorna una instantánea ordenada de las emergencias en la cola.
     * No modifica la cola original.
     *
     * <p>Nótese que la iteración sobre {@code PriorityBlockingQueue} NO
     * garantiza orden; por eso se copia y se ordena explícitamente.</p>
     */
    public List<Emergency> snapshot() {
        List<Emergency> copy = new ArrayList<>(queue);
        Collections.sort(copy);   // orden por prioridad descendente
        return Collections.unmodifiableList(copy);
    }

    /**
     * Elimina emergencias que ya no están en estado PENDING
     * (limpieza preventiva para mantener la cola consistente).
     */
    public void purgeNonPending() {
        queue.removeIf(e -> e.getStatus() != EmergencyStatus.PENDING);
    }
}

