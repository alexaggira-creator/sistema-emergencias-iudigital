package com.emergencias.model;

/**
 * Niveles de gravedad de una emergencia médica.
 * Se utiliza para calcular la prioridad en la cola de despacho.
 */
public enum Severity {
    CRITICAL(4, "CRÍTICO",    "\u001B[31m"),  // Rojo
    SERIOUS(3,  "GRAVE",      "\u001B[33m"),  // Amarillo
    MODERATE(2, "MODERADO",   "\u001B[34m"),  // Azul
    MILD(1,     "LEVE",       "\u001B[32m");  // Verde

    private final int weight;
    private final String label;
    private final String color;

    Severity(int weight, String label, String color) {
        this.weight = weight;
        this.label  = label;
        this.color  = color;
    }

    public int getWeight()  { return weight; }
    public String getLabel(){ return label;  }
    public String getColor(){ return color;  }

    @Override
    public String toString() {
        return color + label + "\u001B[0m";
    }
}

