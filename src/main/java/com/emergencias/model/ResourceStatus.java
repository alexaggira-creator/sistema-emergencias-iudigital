package com.emergencias.model;

/**
 * Estados posibles de un recurso del sistema (ambulancias, médicos, equipos).
 */
public enum ResourceStatus {
    AVAILABLE("DISPONIBLE"),
    EN_ROUTE("EN CAMINO"),
    BUSY("OCUPADO"),
    MAINTENANCE("MANTENIMIENTO");

    private final String label;

    ResourceStatus(String label) { this.label = label; }

    public String getLabel() { return label; }

    @Override
    public String toString() { return label; }
}

