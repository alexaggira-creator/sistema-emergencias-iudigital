package com.emergencias.service;

import com.emergencias.communication.CommunicationBus;
import com.emergencias.concurrent.EmergencyQueue;
import com.emergencias.model.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hilo operador que recibe llamadas de emergencia de la cola de entrada (FIFO
 * de llamadas crudas), las valida y las encola en la {@link EmergencyQueue}
 * con prioridad.
 *
 * <p><strong>Rol en el flujo Producer-Consumer:</strong></p>
 * <pre>
 *   EmergencySimulator ──► rawCallQueue (LinkedBlockingQueue, FIFO)
 *                                │
 *                       EmergencyOperator (valida, 200-500 ms)
 *                                │
 *                       EmergencyQueue (PriorityBlockingQueue)
 *                                │
 *                           Dispatcher
 * </pre>
 *
 * <p>Usar una cola de llamadas crudas separada evita que el operador compita
 * directamente con el despachador sobre la cola prioritaria, preservando el
 * orden de prioridad que necesita el despachador.</p>
 *
 * <p><strong>Terminación segura</strong>: en lugar de bloquear con {@code take()}
 * indefinidamente, se usa {@code poll()} con timeout para revisar periódicamente
 * la señal {@code running} y poder terminar limpiamente.</p>
 */
public class EmergencyOperator extends Thread {

    private final String           operatorId;
    private final EmergencyQueue   rawCallQueue;   // entrada FIFO de llamadas crudas
    private final EmergencyQueue   priorityQueue;  // cola priorizada → Dispatcher
    private final CommunicationBus bus;
    private final AtomicBoolean    running;
    private final AtomicBoolean    active = new AtomicBoolean(false);
    private final AtomicLong       processedCount = new AtomicLong(0);

    public EmergencyOperator(String operatorId,
                             EmergencyQueue rawCallQueue,
                             EmergencyQueue priorityQueue,
                             AtomicBoolean running) {
        super("Operator-" + operatorId);
        setDaemon(true);
        this.operatorId   = operatorId;
        this.rawCallQueue = rawCallQueue;
        this.priorityQueue = priorityQueue;
        this.bus          = CommunicationBus.getInstance();
        this.running      = running;
    }

    @Override
    public void run() {
        System.out.printf("\u001B[36m[OPERADOR-%s] Iniciado y en espera de llamadas...%n\u001B[0m", operatorId);

        while (running.get() || !rawCallQueue.isEmpty()) {
            try {
                // poll con timeout: permite verificar `running` periódicamente
                // y terminar limpiamente cuando no haya más trabajo
                Emergency emergency = rawCallQueue.pollWithTimeout(500);
                if (emergency == null) continue;

                active.set(true);
                try {
                    // Simula el trabajo de validación del operador (200-500 ms)
                    Thread.sleep(200 + (long)(Math.random() * 300));

                    System.out.printf("\u001B[36m[OPERADOR-%s] Validando %s%n\u001B[0m",
                            operatorId, emergency);

                    // Encolar en la cola PRIORITARIA → el Dispatcher consume por prioridad
                    priorityQueue.enqueue(emergency);
                    processedCount.incrementAndGet();

                    bus.publish(new SystemMessage(
                            MessageType.EMERGENCY_RECEIVED,
                            "OPERADOR-" + operatorId,
                            "DISPATCHER",
                            "Emergencia validada y priorizada: " + emergency
                    ));
                } finally {
                    active.set(false);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.printf("\u001B[36m[OPERADOR-%s] Finalizado. Total procesadas: %d%n\u001B[0m",
                operatorId, processedCount.get());
    }

    public long getProcessedCount() { return processedCount.get(); }
    public boolean isActive()       { return active.get(); }
    public String getOperatorId()   { return operatorId; }
}

