package com.joutio.game.domain.port.out;

import com.joutio.game.domain.event.GameEvent;
import com.joutio.microservice.core.domain.model.notification.EventEnvelope;

import java.util.List;

public interface GameEventStorePort {

    List<GameEvent> findEventsByGameId(String gameId);

    /** Flux d'événements d'une partie enrichi de leurs métadonnées (historique de notifications). */
    List<EventEnvelope> findEventEnvelopesByGameId(String gameId);
}
