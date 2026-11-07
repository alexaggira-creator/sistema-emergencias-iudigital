package com.emergencias.service;

import com.emergencias.communication.CommunicationBus;
import com.emergencias.model.*;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hilo que simula el ciclo completo de respuesta de una ambulancia:
 * <ol>
 *   <li>Desplazamiento al lugar de la emergencia (proporcional a distancia).</li>
 *   <li>Atención médica en el sitio.</li>
 *   <li>Traslado al hospital.</li>
 *   <li>Liberación del recurso.</li>
 * </ol>
 *
 * <p>Cada instancia es un hilo de vida corta (non-daemon) que termina al
 * completar su misión.  Publicar mensajes al bus permite que el monitor y
 * otros subsistemas sigan el progreso sin acoplamiento directo.</p>
 */
public class AmbulanceResponseThread extends Thread {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    // Velocidad de simulación: ms por unidad de distancia
    private static final int MS_PER_DISTANCE_UNIT = 300;
    // Tiempo de atención base (ms) según gravedad
    private static final int[] TREATMENT_TIMES = {0, 2000, 3500, 5000, 7000}; // índice = Severity.weight

    private final Ambulance        ambulance;
    private final Emergency        emergency;
    private final CommunicationBus bus;

    public AmbulanceResponseThread(Ambulance ambulance,
                                   Emergency emergency,
                                   CommunicationBus bus) {
        super("AmbResponse-" + THREAD_COUNTER.getAndIncrement());
        setDaemon(false);  // No daemon: debe completar antes de que el sistema finalice
        this.ambulance = ambulance;
        this.emergency = emergency;
        this.bus       = bus;
    }

    @Override
    public void run() {
        try {
            // 1. Viaje al lugar
            travelTo(emergency.getLocation(), "emergencia EM-" + String.format("%03d", emergency.getId()));

            // Cambiar estado: ASSIGNED → IN_PROGRESS
            emergency.transitionStatus(EmergencyStatus.ASSIGNED, EmergencyStatus.IN_PROGRESS);
            ambulance.setBusy();

            bus.publish(new SystemMessage(
                    MessageType.AMBULANCE_ARRIVED,
                    "AMB-" + ambulance.getId(),
                    "HOSPITAL",
                    String.format("AMB-%s llegó a EM-%03d", ambulance.getId(), emergency.getId())
            ));

            System.out.printf("\u001B[32m[AMB-%s] Llegó a escena de EM-%03d. Iniciando atención...%n\u001B[0m",
                    ambulance.getId(), emergency.getId());

            // 2. Atención médica (tiempo según gravedad)
            int treatmentTime = TREATMENT_TIMES[emergency.getSeverity().getWeight()];
            Thread.sleep(treatmentTime);

            // 3. Traslado al hospital (ubicación fija central)
            Location hospital = new Location(50, 50, "Hospital General de Medellín");
            travelTo(hospital, "hospital");

            // 4. Completar emergencia
            emergency.transitionStatus(EmergencyStatus.IN_PROGRESS, EmergencyStatus.COMPLETED);
            ambulance.release();

            System.out.printf("\u001B[32m[AMB-%s] \u001B[1mEM-%03d COMPLETADA\u001B[0m%n",
                    ambulance.getId(), emergency.getId());

            bus.publish(new SystemMessage(
                    MessageType.EMERGENCY_COMPLETED,
                    "AMB-" + ambulance.getId(),
                    "CENTRAL",
                    String.format("EM-%03d completada. AMB-%s disponible.",
                            emergency.getId(), ambulance.getId())
            ));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emergency.setStatus(EmergencyStatus.CANCELLED);
            ambulance.release();
        }
    }

    private void travelTo(Location destination, String destinationName) throws InterruptedException {
        double distance  = ambulance.getCurrentLocation().distanceTo(destination);
        long   travelMs  = (long) (distance * MS_PER_DISTANCE_UNIT);

        System.out.printf("[AMB-%s] En camino a %s (dist: %.1f, tiempo est: %d ms)%n",
                ambulance.getId(), destinationName, distance, travelMs);

        Thread.sleep(Math.min(travelMs, 6000)); // cap: 6 s para demo
        ambulance.setCurrentLocation(destination);
    }
}

