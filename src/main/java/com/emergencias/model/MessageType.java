package com.emergencias.model;

/**
 * Tipos de mensaje que circulan por el bus de comunicación.
 */
public enum MessageType {
    EMERGENCY_RECEIVED("EMERGENCIA RECIBIDA"),
    EMERGENCY_ASSIGNED("EMERGENCIA ASIGNADA"),
    AMBULANCE_DISPATCHED("AMBULANCIA DESPACHADA"),
    AMBULANCE_ARRIVED("AMBULANCIA LLEGÓ"),
    EMERGENCY_COMPLETED("EMERGENCIA COMPLETADA"),
    RESOURCE_STATUS_UPDATE("ACTUALIZACIÓN DE RECURSO"),
    SYSTEM_ALERT("ALERTA DEL SISTEMA");

    private final String label;

    MessageType(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}

