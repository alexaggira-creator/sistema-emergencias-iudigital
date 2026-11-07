package com.emergencias.model;

/**
 * Ubicación geográfica simplificada (coordenadas en la ciudad).
 */
public record Location(double x, double y, String address) {

    /**
     * Calcula la distancia euclidiana hasta otra ubicación.
     */
    public double distanceTo(Location other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public String toString() {
        return address + String.format(" (%.1f, %.1f)", x, y);
    }
}

