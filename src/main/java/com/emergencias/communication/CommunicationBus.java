package com.emergencias.communication;

import com.emergencias.model.SystemMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Bus de comunicación entre subsistemas (Patrón Observer/Message Bus).
 *
 * <p>Permite que los componentes del sistema (operadores, despachadores,
 * ambulancias, hospital) se comuniquen de forma desacoplada publicando y
 * suscribiéndose a mensajes.</p>
 *
 * <p><strong>Thread-safety</strong>:
 * <ul>
 *   <li>{@link ConcurrentHashMap} para el registro de suscriptores.</li>
 *   <li>{@link CopyOnWriteArrayList} para listas de suscriptores por canal
 *       (optimizado para lectura frecuente con escritura infrecuente).</li>
 *   <li>{@link LinkedBlockingQueue} para el historial de mensajes.</li>
 * </ul>
 * </p>
 */
public class CommunicationBus {

    // Suscriptores registrados por canal (tipo de receptor)
    private final Map<String, CopyOnWriteArrayList<MessageListener>> subscribers =
            new ConcurrentHashMap<>();

    // Historial de los últimos mensajes (circuito limitado)
    private final BlockingQueue<SystemMessage> messageLog =
            new LinkedBlockingQueue<>(500);

    // Instancia única – Patrón Singleton
    private static final CommunicationBus INSTANCE = new CommunicationBus();

    private CommunicationBus() {}

    public static CommunicationBus getInstance() { return INSTANCE; }

    // ------------------------------------------------------------------ //
    //  Suscripción                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Registra un oyente para un canal específico.
     *
     * @param channel  nombre del canal (p.ej. "DISPATCHER", "AMBULANCE")
     * @param listener callback que recibe los mensajes
     */
    public void subscribe(String channel, MessageListener listener) {
        subscribers.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>())
                   .add(listener);
    }

    // ------------------------------------------------------------------ //
    //  Publicación                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Publica un mensaje en el bus.
     * <ul>
     *   <li>Notifica a todos los suscriptores del canal del receptor.</li>
     *   <li>Notifica a los suscriptores del canal "ALL" (broadcast).</li>
     * </ul>
     *
     * @param message mensaje a publicar
     */
    public void publish(SystemMessage message) {
        // Guardar en historial (descarta el más antiguo si está lleno)
        if (!messageLog.offer(message)) {
            messageLog.poll();
            messageLog.offer(message);
        }

        // Entregar a suscriptores del canal específico
        notifyChannel(message.receiver(), message);

        // Entregar a suscriptores del broadcast
        notifyChannel("ALL", message);
    }

    private void notifyChannel(String channel, SystemMessage message) {
        List<MessageListener> listeners = subscribers.getOrDefault(
                channel, new CopyOnWriteArrayList<>());
        for (MessageListener listener : listeners) {
            try {
                listener.onMessage(message);
            } catch (Exception ex) {
                System.err.println("[BUS] Error al notificar canal " + channel + ": " + ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Historial                                                          //
    // ------------------------------------------------------------------ //

    public List<SystemMessage> getMessageLog() {
        return Collections.unmodifiableList(new ArrayList<>(messageLog));
    }

    public int getMessageCount() { return messageLog.size(); }
}

