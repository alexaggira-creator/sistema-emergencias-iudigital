package com.emergencias.communication;

import com.emergencias.model.SystemMessage;

/**
 * Interfaz funcional para suscriptores del bus de comunicación.
 */
@FunctionalInterface
public interface MessageListener {
    void onMessage(SystemMessage message);
}

