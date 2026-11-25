package com.joutio.game.application.handler.event;

import com.joutio.game.domain.event.GameEvent.GameStartedEvent;
import com.joutio.game.domain.query.GameQuery.GetGameEventsQuery;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameLiveStreamHandlerTest {

    private static final Instant AT = Instant.parse("2026-11-25T10:00:00Z");

    private final QueryUpdateEmitter emitter = mock(QueryUpdateEmitter.class);
    private final GameLiveStreamHandler handler = new GameLiveStreamHandler(emitter);

    @Test
    @SuppressWarnings("unchecked")
    void emits_the_event_as_its_history_envelope_to_the_subscribers_of_the_same_game() {
        GameStartedEvent event = new GameStartedEvent("g1", AT, AT.plusSeconds(3));
        GenericDomainEventMessage<GameStartedEvent> message =
                new GenericDomainEventMessage<>("GameAggregate", "g1", 4, event);

        handler.on(event, message);

        ArgumentCaptor<Predicate<GetGameEventsQuery>> filter = ArgumentCaptor.forClass(Predicate.class);
        ArgumentCaptor<EventEnvelope> envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(emitter).emit(eq(GetGameEventsQuery.class), filter.capture(), envelope.capture());
        assertTrue(filter.getValue().test(new GetGameEventsQuery("g1")));
        assertFalse(filter.getValue().test(new GetGameEventsQuery("g2")));
        assertEquals("g1", envelope.getValue().aggregateId());
        assertEquals(4, envelope.getValue().sequenceNumber());
        assertEquals(event, envelope.getValue().payload());
        assertEquals(message.getTimestamp(), envelope.getValue().timestamp());
    }
}
