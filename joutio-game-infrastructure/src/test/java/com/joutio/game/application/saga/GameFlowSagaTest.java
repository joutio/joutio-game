package com.joutio.game.application.saga;

import com.joutio.game.domain.command.GameCommand;
import com.joutio.game.domain.event.GameEvent;
import com.joutio.game.domain.model.BotAnswer;
import com.joutio.game.domain.model.BotDifficulty;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.model.GameQuestionChoice;
import com.joutio.game.domain.model.GameRoundType;
import com.joutio.game.domain.model.GameRules;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

/**
 * Test in-memory de l'orchestration {@link GameFlowSaga} via {@link SagaTestFixture}. La partie
 * démarre immédiatement à la création : la saga n'émet aucune commande à la création (elle
 * planifie l'expiration), puis l'intro ({@code MATCH_INTRO}) déclenche le premier round.
 *
 * <p>Déterminisme : les commandes émises par les deadlines reprennent les instants et la réponse du
 * bot portés par les événements, jamais l'horloge ni un aléa.
 */
class GameFlowSagaTest {

    private static final String GAME_ID = "game-1";
    private static final String PLAYER_1 = "player-1";
    private static final String PLAYER_2 = "player-2";

    private final SagaTestFixture<GameFlowSaga> fixture =
            new SagaTestFixture<>(GameFlowSaga.class);

    @Test
    void gameCreated_dispatchesNoCommand() {
        fixture.givenNoPriorActivity()
                .whenPublishingA(gameCreated(GamePlayerType.HUMAN))
                .expectNoDispatchedCommands();
    }

    @Test
    void botAnswers_atTheDrawnInstant_forTheRevealedRound() {
        Instant revealedAt = fixture.currentTime();
        BotAnswer bot = new BotAnswer(GameQuestionChoice.C, revealedAt.plusMillis(2500));

        fixture.givenAPublished(gameCreated(GamePlayerType.BOT))
                .andThenAPublished(revealed(revealedAt, bot))
                .whenTimeAdvancesTo(bot.answerAt())
                .expectDispatchedCommands(new GameCommand.BotAnswerCommand(GAME_ID, GameRoundType.ROUND_1));
    }

    @Test
    void roundExpired_expiresTheRevealedRound() {
        Instant revealedAt = fixture.currentTime();
        GameEvent.QuestionRevealedEvent revealed = revealed(revealedAt, null);

        fixture.givenAPublished(gameCreated(GamePlayerType.HUMAN))
                .andThenAPublished(revealed)
                // L'expiration attend la grâce des réponses retardées par le réseau.
                .whenTimeAdvancesTo(revealed.answerDeadlineAt().plusMillis(GameRules.ANSWER_GRACE_MS))
                .expectDispatchedCommands(new GameCommand.ExpireRoundCommand(GAME_ID, GameRoundType.ROUND_1));
    }

    @Test
    void roundExpiry_waitsForTheAnswerGrace() {
        Instant revealedAt = fixture.currentTime();
        GameEvent.QuestionRevealedEvent revealed = revealed(revealedAt, null);

        fixture.givenAPublished(gameCreated(GamePlayerType.HUMAN))
                .andThenAPublished(revealed)
                .whenTimeAdvancesTo(revealed.answerDeadlineAt())
                .expectNoDispatchedCommands();
    }

    /**
     * Sous charge, l'échéance peut passer avant la réponse du bot (deadline en retard) : la saga n'envoie plus de
     * réponse vide pour le bot, l'agrégat applique la réponse tirée (2026-11-19).
     */
    @Test
    void lateBotDeadline_stillOnlyExpiresTheRound() {
        Instant revealedAt = fixture.currentTime();
        BotAnswer bot = new BotAnswer(GameQuestionChoice.C, revealedAt.plusSeconds(GameRules.ROUND_TIMEOUT_SECONDS));
        GameEvent.QuestionRevealedEvent revealed = revealed(revealedAt, bot);

        fixture.givenAPublished(gameCreated(GamePlayerType.BOT))
                .andThenAPublished(revealed)
                .whenTimeAdvancesTo(revealed.answerDeadlineAt().plusMillis(GameRules.ANSWER_GRACE_MS))
                .expectDispatchedCommands(
                        new GameCommand.BotAnswerCommand(GAME_ID, GameRoundType.ROUND_1),
                        new GameCommand.ExpireRoundCommand(GAME_ID, GameRoundType.ROUND_1));
    }

    private GameEvent.GameCreatedEvent gameCreated(GamePlayerType player2Type) {
        return new GameEvent.GameCreatedEvent(
                GAME_ID,
                "topic-1",
                PLAYER_1,
                "Alpha",
                PLAYER_2,
                "Bravo",
                player2Type,
                List.of(),
                player2Type == GamePlayerType.BOT ? BotDifficulty.NORMAL : null,
                null,
                null,
                fixture.currentTime()
        );
    }

    private static GameEvent.QuestionRevealedEvent revealed(Instant revealedAt, BotAnswer botAnswer) {
        return new GameEvent.QuestionRevealedEvent(
                GAME_ID,
                GameRoundType.ROUND_1,
                revealedAt,
                revealedAt.plusSeconds(GameRules.ROUND_TIMEOUT_SECONDS),
                botAnswer
        );
    }
}
