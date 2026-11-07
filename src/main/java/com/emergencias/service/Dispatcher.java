package com.emergencias.service;

import com.emergencias.communication.CommunicationBus;
import com.emergencias.concurrent.AmbulancePool;
import com.emergencias.concurrent.EmergencyQueue;
import com.emergencias.model.*;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hilo despachador: toma emergencias de la {@link EmergencyQueue} (cola
 * priorizada), busca la ambulancia disponible más cercana y coordina el
 * despacho.
 *
 * <p><strong>Por qué el Dispatcher consume directamente de la PBQ:</strong>
 * Si el Dispatcher consumiera de una {@code LinkedBlockingQueue} (FIFO), el
 * orden de prioridad calculado en {@link Emergency#computePriority()} se
 * perdería completamente.  Al consumir de la {@code PriorityBlockingQueue},
 * cada {@code pollWithTimeout()} extrae la emergencia con mayor prioridad
 * en ese instante, respetando gravedad + tiempo de espera.</p>
 *
 * <p><strong>Algoritmo de priorización:</strong>
 * <pre>  P = gravedad × 1000 + espera_segundos × 0.5</pre>
 * La gravedad domina la prioridad (CRITICAL siempre supera a MILD reciente),
 * pero el tiempo de espera evita la inanición de emergencias menos graves.</p>
 *
 * <p><strong>Tolerancia a falta de ambulancias:</strong> si no hay ambulancias
 * disponibles, la emergencia se devuelve a la misma {@link EmergencyQueue}
 * (cola prioritaria) para reintentarse luego.  Esto preserva su prioridad
 * y evita duplicados porque la emergencia solo existe en un lugar a la vez.</p>
 *
 * <p><strong>Prevención de deadlocks:</strong> no hay adquisición anidada de
 * monitores.  El único punto de exclusión es el CAS interno de
 * {@link Ambulance#tryAcquire()}, que es lock-free.</p>
 */
public class Dispatcher extends Thread {

    private static final int BASE_RETRY_WAIT_MS = 1_500;
    private static final int MAX_RETRY_WAIT_MS  = 3_000;

    private final String           dispatcherId;
    private final EmergencyQueue   priorityQueue;   // PriorityBlockingQueue compartida
    private final AmbulancePool    ambulancePool;
    private final CommunicationBus bus;
    private final AtomicBoolean    running;
    private final AtomicLong       dispatchedCount = new AtomicLong(0);

    public Dispatcher(String dispatcherId,
                      EmergencyQueue priorityQueue,
                      AmbulancePool ambulancePool,
                      AtomicBoolean running) {
        super("Dispatcher-" + dispatcherId);
        setDaemon(true);
        this.dispatcherId  = dispatcherId;
        this.priorityQueue = priorityQueue;
        this.ambulancePool = ambulancePool;
        this.bus           = CommunicationBus.getInstance();
        this.running       = running;
    }

    @Override
    public void run() {
        System.out.printf("\u001B[35m[DESPACHADOR-%s] Iniciado.%n\u001B[0m", dispatcherId);

        while (running.get() || !priorityQueue.isEmpty()) {
            try {
                // poll con timeout: permite verificar `running` sin bloquearse indefinidamente
                Emergency emergency = priorityQueue.pollWithTimeout(500);
                if (emergency == null) continue;

                // Descartar emergencias que ya fueron procesadas por otro despachador
                if (emergency.getStatus() != EmergencyStatus.PENDING) continue;

                dispatch(emergency);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.printf("\u001B[35m[DESPACHADOR-%s] Finalizado. Total despachadas: %d%n\u001B[0m",
                dispatcherId, dispatchedCount.get());
    }

    // ------------------------------------------------------------------ //
    //  Lógica de despacho                                                 //
    // ------------------------------------------------------------------ //

    private void dispatch(Emergency emergency) throws InterruptedException {
        System.out.printf("\u001B[35m[DESPACHADOR-%s] Procesando %s%n\u001B[0m",
                dispatcherId, emergency);

        // Buscar la ambulancia disponible más cercana (CAS interno)
        var optAmbulance = ambulancePool.findNearest(emergency.getLocation());

        if (optAmbulance.isEmpty()) {
            // Sin recursos: devolver a la cola PRIORITARIA (no FIFO)
            // La emergencia acumula tiempo de espera → su prioridad seguirá subiendo
            System.out.printf("\u001B[33m[DESPACHADOR-%s] Sin ambulancias disponibles. " +
                              "EM-%03d espera en cola prioritaria...%n\u001B[0m",
                              dispatcherId, emergency.getId());
            priorityQueue.enqueue(emergency);  // Reinsertar primero para mantenerla en cola
            // Espera prudente con jitter para evitar busy-waiting y estampida de reintentos
            long waitMs = BASE_RETRY_WAIT_MS + (long)(Math.random() * (MAX_RETRY_WAIT_MS - BASE_RETRY_WAIT_MS));
            Thread.sleep(waitMs);
            return;
        }

        Ambulance ambulance = optAmbulance.get();

        // Transición de estado atómica: PENDING → ASSIGNED
        // Si otro despachador ya tomó esta emergencia, el CAS falla → liberar ambulancia
        if (!emergency.transitionStatus(EmergencyStatus.PENDING, EmergencyStatus.ASSIGNED)) {
            ambulance.release();
            return;
        }

        // Registrar asignación
        emergency.setAssignedAmbulanceId(ambulance.getId());
        emergency.setAttendedAt(Instant.now());
        ambulance.setAssignedEmergencyId(emergency.getId());

        dispatchedCount.incrementAndGet();

        System.out.printf("\u001B[35m[DESPACHADOR-%s] \u001B[32m✓ EM-%03d asignada a AMB-%s (dist: %.1f)%n\u001B[0m",
                dispatcherId, emergency.getId(), ambulance.getId(),
                ambulance.getCurrentLocation().distanceTo(emergency.getLocation()));

        bus.publish(new SystemMessage(
                MessageType.EMERGENCY_ASSIGNED,
                "DESPACHADOR-" + dispatcherId,
                "AMB-" + ambulance.getId(),
                String.format("EM-%03d → AMB-%s | %s | dist: %.1f",
                        emergency.getId(), ambulance.getId(), emergency.getSeverity(),
                        ambulance.getCurrentLocation().distanceTo(emergency.getLocation()))
        ));

        // Lanzar hilo de respuesta de ambulancia (simula el viaje y atención)
        new AmbulanceResponseThread(ambulance, emergency, bus).start();
    }

    public long getDispatchedCount() { return dispatchedCount.get(); }
    public String getDispatcherId()  { return dispatcherId; }
}
