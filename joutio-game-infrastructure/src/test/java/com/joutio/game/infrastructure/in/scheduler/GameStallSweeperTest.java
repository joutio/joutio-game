package com.joutio.game.infrastructure.in.scheduler;

import com.joutio.game.domain.command.GameCommand;
import com.joutio.game.domain.model.GameDeadline;
import com.joutio.game.domain.port.out.GameRepositoryPort;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GameStallSweeperTest {

    private final GameRepositoryPort repository = mock(GameRepositoryPort.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final GameStallSweeper sweeper = new GameStallSweeper(repository, commandGateway);

    @Test
    void sweepCancelsStalledGames() {
        when(repository.findStalledInProgressIds(any(Instant.class))).thenReturn(List.of("game-1"));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        sweeper.sweep();

        verify(commandGateway).send(new GameCommand.CancelGameCommand("game-1", GameDeadline.GAME_STALLED_REASON));
    }

    @Test
    void sweepIsNoOpWithoutStalledGames() {
        when(repository.findStalledInProgressIds(any(Instant.class))).thenReturn(List.of());

        sweeper.sweep();

        verifyNoInteractions(commandGateway);
    }
}
