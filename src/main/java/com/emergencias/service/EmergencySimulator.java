package com.emergencias.service;

import com.emergencias.communication.CommunicationBus;
import com.emergencias.concurrent.EmergencyQueue;
import com.emergencias.model.*;
import com.emergencias.monitor.MonitoringSystem;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hilo que simula la llegada aleatoria de llamadas de emergencia.
 *
 * <p>Genera emergencias con gravedad, ubicación y descripción aleatorias a
 * intervalos variables, imitando la naturaleza impredecible de las emergencias
 * reales.  Publica cada nueva emergencia en el {@link CommunicationBus} y la
 * encola en la {@link EmergencyQueue}.</p>
 */
public class EmergencySimulator extends Thread {

    // Intervalo mínimo y máximo entre llamadas (ms)
    private static final int MIN_INTERVAL_MS = 1_500;
    private static final int MAX_INTERVAL_MS = 4_000;

    private static final String[] DESCRIPTIONS = {
            "Accidente de tráfico en avenida principal",
            "Infarto agudo al miocardio",
            "Caída con fractura de cadera",
            "Quemaduras por incendio doméstico",
            "Parto prematuro en domicilio",
            "Convulsiones epilépticas",
            "Intoxicación por medicamentos",
            "Herida de arma cortante",
            "Reacción alérgica grave (anafilaxia)",
            "Accidente en construcción",
            "Dolor torácico agudo",
            "Dificultad respiratoria severa",
            "Ahogamiento en piscina",
            "Golpe de calor",
            "Agresión con lesiones múltiples"
    };

    private static final String[] ADDRESSES = {
            "Cra 70 #80-20, Laureles",
            "Cl 10 #43E-31, El Poblado",
            "Av. El Poblado #17A-05",
            "Cra 65 #48-30, Belén",
            "Cl 44 #68-100, Estadio",
            "Tv. Superior #48D-01, Envigado",
            "Cra 48 #18-50, La Candelaria",
            "Cl 50 #80-10, San Javier",
            "Av. Regional Km 3, Itagüí",
            "Cra 32 #9-20, Buenos Aires"
    };

    private final EmergencyQueue   emergencyQueue;
    private final CommunicationBus bus;
    private final AtomicBoolean    running;
    private final Random           rng = new Random();
    private final int              totalEmergencies;
    private MonitoringSystem       monitor;

    public EmergencySimulator(EmergencyQueue emergencyQueue,
                              AtomicBoolean running,
                              int totalEmergencies) {
        super("EmergencySimulator");
        setDaemon(true);
        this.emergencyQueue   = emergencyQueue;
        this.bus              = CommunicationBus.getInstance();
        this.running          = running;
        this.totalEmergencies = totalEmergencies;
    }

    public void setMonitor(MonitoringSystem monitor) {
        this.monitor = monitor;
    }

    @Override
    public void run() {
        int generated = 0;
        while (running.get() && generated < totalEmergencies) {
            try {
                // Intervalo aleatorio entre llamadas
                int delay = MIN_INTERVAL_MS + rng.nextInt(MAX_INTERVAL_MS - MIN_INTERVAL_MS);
                Thread.sleep(delay);

                Emergency emergency = generateRandom();
                emergencyQueue.enqueue(emergency);

                // Registrar en el monitor si está disponible
                if (monitor != null) {
                    monitor.register(emergency);
                }

                bus.publish(new SystemMessage(
                        MessageType.EMERGENCY_RECEIVED,
                        "SIMULADOR",
                        "CENTRAL",
                        emergency.toString()
                ));

                generated++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Generación de emergencia aleatoria                                 //
    // ------------------------------------------------------------------ //

    private Emergency generateRandom() {
        Severity  severity    = randomSeverity();
        Location  location    = randomLocation();
        String    description = DESCRIPTIONS[rng.nextInt(DESCRIPTIONS.length)];
        return new Emergency(severity, location, description);
    }

    private Severity randomSeverity() {
        // Distribución realista: más leves que críticas
        int roll = rng.nextInt(100);
        if (roll < 15)  return Severity.CRITICAL;
        if (roll < 40)  return Severity.SERIOUS;
        if (roll < 70)  return Severity.MODERATE;
        return Severity.MILD;
    }

    private Location randomLocation() {
        int idx     = rng.nextInt(ADDRESSES.length);
        double x    = 10 + rng.nextDouble() * 80;
        double y    = 10 + rng.nextDouble() * 80;
        return new Location(x, y, ADDRESSES[idx]);
    }
}
