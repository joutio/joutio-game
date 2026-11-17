package com.joutio.game.application.saga;

import com.joutio.game.domain.command.GameCommand;
import com.joutio.game.domain.event.GameEvent;
import com.joutio.game.domain.model.*;
import lombok.Getter;
import lombok.Setter;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

/**
 * Orchestration du cycle de vie d'une partie :
 * <ul>
 *   <li>la partie démarre immédiatement à sa création (présence garantie en amont par le
 *       salon/l'appariement côté {@code joutio-matchmaking}) ;</li>
 *   <li>les deadlines de phase sont planifiées aux instants absolus portés par les événements ;</li>
 *   <li>la réponse du bot (choix et instant) est tirée par l'agrégat et portée par
 *       {@code QuestionRevealedEvent}.</li>
 * </ul>
 *
 * <p>Le serveur est seule source de vérité du temps. Déterministe : ni {@code Random} ni
 * {@code Instant.now()}, un rejeu prend les mêmes décisions.
 */
@Saga
@ProcessingGroup("game-flow-saga")
public class GameFlowSaga {

    private static final Logger logger = LoggerFactory.getLogger(GameFlowSaga.class);

    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient DeadlineManager deadlineManager;

    @Getter
    @Setter
    private String gameId;

    /** La partie a démarré : l'expiration la clôture au lieu de l'annuler. */
    @Getter
    @Setter
    private boolean started;

    @Getter
    @Setter
    private String expiredDeadlineId;

    @Getter
    @Setter
    private String player1Id;

    @Getter
    @Setter
    private String player2Id;

    @Getter
    @Setter
    private GameRoundType currentRound;

    /** Fin de la fenêtre de réponse de la manche : instant des réponses vides à l'expiration. */
    @Getter
    @Setter
    private Instant answerDeadlineAt;

    /** Réponse du bot pour la manche en cours, {@code null} entre humains. */
    @Getter
    @Setter
    private BotAnswer botAnswer;

    @Getter
    @Setter
    private int answersInCurrentRound;

    @Getter
    @Setter
    private boolean player1Answered;

    @Getter
    @Setter
    private boolean player2Answered;

    @Getter
    @Setter
    private String matchIntroDeadlineId;

    @Getter
    @Setter
    private String questionRevealDeadlineId;

    @Getter
    @Setter
    private String roundDeadlineId;

    @Getter
    @Setter
    private String nextRoundDeadlineId;

    @Getter
    @Setter
    private String botDeadlineId;

    @StartSaga
    @SagaEventHandler(associationProperty = "gameId")
    public void on(GameEvent.GameCreatedEvent event) {
        this.gameId = event.gameId();
        this.player1Id = event.player1Id();
        this.player2Id = event.player2Id();
        this.expiredDeadlineId = deadlineManager.schedule(
                event.createdAt().plus(GameDeadline.GAME_EXPIRED_TIMEOUT),
                GameDeadline.GAME_EXPIRED
        );
    }

    @SagaEventHandler(associationProperty = "gameId")
    public void on(GameEvent.GameStartedEvent event) {
        this.started = true;
        matchIntroDeadlineId = deadlineManager.schedule(
                event.firstRoundAt(),
                GameDeadline.MATCH_INTRO
        );
    }

    @DeadlineHandler(deadlineName = GameDeadline.MATCH_INTRO)
    public void onMatchIntro() {
        commandGateway.send(new GameCommand.StartRoundCommand(gameId));
    }

    /**
     * Filet de sécurité : une partie ni terminée ni annulée sous {@code GAME_EXPIRED_TIMEOUT}
     * est close d'office (annulation avant démarrage, clôture au score en cours de jeu).
     */
    @DeadlineHandler(deadlineName = GameDeadline.GAME_EXPIRED)
    public void onGameExpired() {
        logger.warn("Partie expirée après {}h: gameId={}", GameRules.GAME_TIMEOUT_HOURS, gameId);
        if (started) {
            commandGateway.send(new GameCommand.EndGameCommand(gameId));
            return;
        }
        commandGateway.send(new GameCommand.CancelGameCommand(gameId, "GAME_EXPIRED"));
    }

    @SagaEventHandler(associationProperty = "gameId")
    public void on(GameEvent.RoundStartedEvent event) {
        this.currentRound = event.round();

        questionRevealDeadlineId = deadlineManager.schedule(
                event.revealAt(),
                GameDeadline.QUESTION_REVEAL
        );
    }

    @DeadlineHandler(deadlineName = GameDeadline.QUESTION_REVEAL)
    public void onQuestionReveal() {
        commandGateway.send(new GameCommand.RevealQuestionCommand(gameId));
    }

    @SagaEventHandler(associationProperty = "gameId")
    public void on(GameEvent.QuestionRevealedEvent event) {
        this.answersInCurrentRound = 0;
        this.player1Answered = false;
        this.player2Answered = false;
        this.answerDeadlineAt = event.answerDeadlineAt();
        this.botAnswer = event.botAnswer();

        roundDeadlineId = deadlineManager.schedule(
                event.answerDeadlineAt(),
                GameDeadline.ROUND_EXPIRED
        );

        if (botAnswer != null) {
            botDeadlineId = deadlineManager.schedule(botAnswer.answerAt(), GameDeadline.BOT_ANSWERS);
        }
    }

    @DeadlineHandler(deadlineName = GameDeadline.BOT_ANSWERS)
    public void onBotAnswers() {
        commandGateway.send(
                new GameCommand.AnswerQuestionCommand(gameId, player2Id, botAnswer.choice(), botAnswer.answerAt())
        );
    }

    @SagaEventHandler(associationProperty = "gameId")
    public void on(GameEvent.QuestionAnsweredEvent event) {
        answersInCurrentRound++;
        if (event.playerId().equals(player1Id)) {
            player1Answered = true;
        } else {
            player2Answered = true;
        }

        if (answersInCurrentRound >= 2) {
            cancelRoundDeadlines();
            commandGateway.send(new GameCommand.CloseRoundCommand(gameId));
        }
    }

    @DeadlineHandler(deadlineName = GameDeadline.ROUND_EXPIRED)
    public void onRoundExpired() {
        if (!player1Answered) {
            commandGateway.send(new GameCommand.AnswerQuestionCommand(gameId, player1Id, null, answerDeadlineAt));
        }
        if (!player2Answered) {
            commandGateway.send(new GameCommand.AnswerQuestionCommand(gameId, player2Id, null, answerDeadlineAt));
        }
    }

    @SagaEventHandler(associationProperty = "gameId")
    public void on(GameEvent.RoundClosedEvent event) {
        cancelQuestionRevealDeadline();

        if (event.nextRound() != null) {
            nextRoundDeadlineId = deadlineManager.schedule(
                    event.nextRoundAt(),
                    GameDeadline.NEXT_ROUND_STARTS
            );
        } else {
            commandGateway.send(new GameCommand.EndGameCommand(gameId));
        }
    }

    @DeadlineHandler(deadlineName = GameDeadline.NEXT_ROUND_STARTS)
    public void onNextRoundStarts() {
        commandGateway.send(new GameCommand.StartRoundCommand(gameId));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "gameId")
    public void on(GameEvent.GameEndedEvent event) {
        logger.info("Game ended: gameId={}, winnerId={}", gameId, event.winnerId());
        cancelAll();
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "gameId")
    public void on(GameEvent.GameCancelledEvent event) {
        cancelAll();
    }

    private void cancelAll() {
        cancelMatchIntroDeadline();
        cancelQuestionRevealDeadline();
        cancelRoundDeadlines();
        cancelNextRoundDeadline();
        cancelExpiredDeadline();
    }

    private void cancelMatchIntroDeadline() {
        if (matchIntroDeadlineId != null) {
            deadlineManager.cancelSchedule(GameDeadline.MATCH_INTRO, matchIntroDeadlineId);
            matchIntroDeadlineId = null;
        }
    }

    private void cancelQuestionRevealDeadline() {
        if (questionRevealDeadlineId != null) {
            deadlineManager.cancelSchedule(GameDeadline.QUESTION_REVEAL, questionRevealDeadlineId);
            questionRevealDeadlineId = null;
        }
    }

    private void cancelRoundDeadlines() {
        if (roundDeadlineId != null) {
            deadlineManager.cancelSchedule(GameDeadline.ROUND_EXPIRED, roundDeadlineId);
            roundDeadlineId = null;
        }
        if (botDeadlineId != null) {
            deadlineManager.cancelSchedule(GameDeadline.BOT_ANSWERS, botDeadlineId);
            botDeadlineId = null;
        }
    }

    private void cancelNextRoundDeadline() {
        if (nextRoundDeadlineId != null) {
            deadlineManager.cancelSchedule(GameDeadline.NEXT_ROUND_STARTS, nextRoundDeadlineId);
            nextRoundDeadlineId = null;
        }
    }

    private void cancelExpiredDeadline() {
        if (expiredDeadlineId != null) {
            deadlineManager.cancelSchedule(GameDeadline.GAME_EXPIRED, expiredDeadlineId);
            expiredDeadlineId = null;
        }
    }
}
