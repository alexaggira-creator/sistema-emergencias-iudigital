package com.emergencias.model;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Representa una ambulancia del sistema de emergencias.
 *
 * <p>Thread-safety garantizada mediante {@link AtomicReference} para el
 * estado y {@code volatile} para la ubicación actual.  El método
 * {@link #tryAcquire()} usa CAS (compare-and-set) para que únicamente
 * un despachador pueda reservar la ambulancia simultáneamente.</p>
 */
public class Ambulance {

    private final String id;
    private final String crewName;
    private volatile Location currentLocation;

    private final AtomicReference<ResourceStatus> status;
    private volatile Integer assignedEmergencyId;

    public Ambulance(String id, String crewName, Location baseLocation) {
        this.id              = id;
        this.crewName        = crewName;
        this.currentLocation = baseLocation;
        this.status          = new AtomicReference<>(ResourceStatus.AVAILABLE);
    }

    // ------------------------------------------------------------------ //
    //  Reserva / liberación atómica                                      //
    // ------------------------------------------------------------------ //

    /**
     * Intenta adquirir la ambulancia de forma atómica (CAS).
     *
     * @return {@code true} si esta llamada logró reservarla;
     *         {@code false} si ya estaba ocupada.
     */
    public boolean tryAcquire() {
        return status.compareAndSet(ResourceStatus.AVAILABLE, ResourceStatus.EN_ROUTE);
    }

    /**
     * Libera la ambulancia y la devuelve al pool disponible.
     */
    public void release() {
        assignedEmergencyId = null;
        status.set(ResourceStatus.AVAILABLE);
    }

    // ------------------------------------------------------------------ //
    //  Getters / Setters                                                  //
    // ------------------------------------------------------------------ //
    public String          getId()                   { return id; }
    public String          getCrewName()             { return crewName; }
    public Location        getCurrentLocation()      { return currentLocation; }
    public ResourceStatus  getStatus()               { return status.get(); }
    public Integer         getAssignedEmergencyId()  { return assignedEmergencyId; }

    public void setCurrentLocation(Location loc)      { this.currentLocation = loc; }
    public void setAssignedEmergencyId(Integer eId)   { this.assignedEmergencyId = eId; }
    public void setBusy()                             { status.set(ResourceStatus.BUSY); }

    @Override
    public String toString() {
        return String.format("[AMB-%s | %s | %s]", id, crewName, status.get());
    }
}

