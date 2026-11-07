package com.emergencias.model;

/**
 * Estado del ciclo de vida de una emergencia.
 */
public enum EmergencyStatus {
    PENDING("PENDIENTE"),
    ASSIGNED("ASIGNADO"),
    IN_PROGRESS("EN CURSO"),
    COMPLETED("COMPLETADO"),
    CANCELLED("CANCELADO");

    private final String label;

    EmergencyStatus(String label) { this.label = label; }

    public String getLabel() { return label; }

    @Override
    public String toString() { return label; }
}

