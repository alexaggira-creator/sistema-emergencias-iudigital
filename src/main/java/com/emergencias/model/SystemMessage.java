package com.emergencias.model;

import java.time.Instant;

/**
 * Mensaje intercambiado entre subsistemas a través del
 * {@link com.emergencias.communication.CommunicationBus}.
 *
 * <p>Es inmutable; se crea una vez y se publica en el bus.</p>
 */
public record SystemMessage(
        MessageType type,
        String      sender,
        String      receiver,
        String      payload,
        Instant     timestamp
) {
    public SystemMessage(MessageType type, String sender, String receiver, String payload) {
        this(type, sender, receiver, payload, Instant.now());
    }

    @Override
    public String toString() {
        return String.format("[%s → %s] %s: %s", sender, receiver, type, payload);
    }
}

