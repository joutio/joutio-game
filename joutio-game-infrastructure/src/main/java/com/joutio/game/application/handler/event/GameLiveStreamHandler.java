package com.joutio.game.application.handler.event;

import com.joutio.game.domain.event.GameEvent;
import com.joutio.game.domain.query.GameQuery.GetGameEventsQuery;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.springframework.stereotype.Component;

/**
 * Flux d'une partie en vue vivante : chaque événement est émis aux abonnés de {@link GetGameEventsQuery} de la même
 * partie, sous la forme de l'historique ({@link EventEnvelope}, versionné par sa séquence).
 *
 * <p>Groupe local ({@code joutio.axon.local-groups}) : le handler tourne dans l'unité de travail de la commande, sur le
 * pod qui l'a traitée, et l'émission part à son commit, sans passer par Kafka. Le BFF est abonné à toutes les
 * répliques, donc reçoit l'événement quelle que soit celle qui l'a publié.</p>
 */
@Component
@ProcessingGroup("game-live")
public class GameLiveStreamHandler {

    private final QueryUpdateEmitter emitter;

    public GameLiveStreamHandler(QueryUpdateEmitter emitter) {
        this.emitter = emitter;
    }

    @EventHandler
    public void on(GameEvent event, DomainEventMessage<?> message) {
        EventEnvelope envelope = EventEnvelope.of(message.getAggregateIdentifier(), message.getSequenceNumber(),
                message.getTimestamp(), event);
        emitter.emit(GetGameEventsQuery.class, query -> query.gameId().equals(event.gameId()), envelope);
    }
}
