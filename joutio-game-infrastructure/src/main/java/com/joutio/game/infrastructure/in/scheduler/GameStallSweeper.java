package com.joutio.game.infrastructure.in.scheduler;

import com.joutio.game.domain.command.GameCommand;
import com.joutio.game.domain.model.GameDeadline;
import com.joutio.game.domain.port.out.GameRepositoryPort;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Filet des parties dont la deadline de manche (en mémoire) a été perdue avec son pod : une partie
 * en cours sans activité depuis {@link GameDeadline#GAME_STALL} est annulée
 * ({@link GameDeadline#GAME_STALLED_REASON}) plutôt que d'attendre {@code GAME_EXPIRED} (24 h).
 * Commande idempotente (sans effet sur une partie close). Planifié par {@link SweepTaskConfiguration}.
 */
@Component
public class GameStallSweeper {

    private static final Logger logger = LoggerFactory.getLogger(GameStallSweeper.class);

    private final GameRepositoryPort gameRepositoryPort;
    private final CommandGateway commandGateway;

    public GameStallSweeper(GameRepositoryPort gameRepositoryPort, CommandGateway commandGateway) {
        this.gameRepositoryPort = gameRepositoryPort;
        this.commandGateway = commandGateway;
    }

    public void sweep() {
        gameRepositoryPort.findStalledInProgressIds(Instant.now().minus(GameDeadline.GAME_STALL))
                .forEach(gameId -> {
                    logger.warn("Partie bloquée, annulation: gameId={}", gameId);
                    commandGateway.send(new GameCommand.CancelGameCommand(gameId, GameDeadline.GAME_STALLED_REASON))
                            .exceptionally(error -> {
                                logger.debug("Annulation de partie bloquée ignorée: gameId={} ({})",
                                        gameId, error.getMessage());
                                return null;
                            });
                });
    }
}
