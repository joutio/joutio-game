package com.joutio.game.domain.exception;

import com.joutio.microservice.core.domain.exception.ProblemCategory;
import com.joutio.game.domain.model.GamePlayer;
import com.joutio.game.domain.model.GameRules;

import java.util.Map;

/**
 * Exceptions spécifiques au domaine Game.
 * Chaque exception décrit un cas métier précis — pas d'exception générique paramétrée.
 */
public interface GameExceptions {

    // ── Création ──

    class MissingTopicProblem extends GameProblem {
        public MissingTopicProblem(String gameId) {
            super(gameId, "urn:joutio:game:missingTopic",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Topic required",
                    "A topic is required to create game " + gameId, null);
        }
    }

    class MissingPlayerProblem extends GameProblem {
        public MissingPlayerProblem(String gameId, GamePlayer gamePlayer) {
            super(gameId, "urn:joutio:game:missingPlayer",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Player required",
                    gamePlayer.name() + " is required to create game " + gameId,
                    Map.of("player", gamePlayer.name()));
        }
    }

    class MissingPlayerIdProblem extends GameProblem {
        public MissingPlayerIdProblem(String gameId) {
            super(gameId, "urn:joutio:game:missingPlayerId",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Player ID required",
                    "A player ID is required to perform this action on game " + gameId, null);
        }
    }


    class MissingTimestampProblem extends GameProblem {
        public MissingTimestampProblem(String gameId) {
            super(gameId, "urn:joutio:game:missingTimestamp",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Timestamp required",
                    "A timestamp is required to answer a question in game " + gameId, null);
        }
    }

    class NotEnoughQuestionsProblem extends GameProblem {
        public NotEnoughQuestionsProblem(String gameId, String topicId, int available) {
            super(gameId, "urn:joutio:game:notEnoughQuestions",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Not enough questions",
                    "Topic " + topicId + " has " + available + " prepared questions, "
                            + GameRules.TOTAL_ROUNDS + " required to create game " + gameId,
                    Map.of("topicId", topicId, "available", available,
                            "required", GameRules.TOTAL_ROUNDS));
        }
    }

    // ── Lookup ──

    class GameNotFoundProblem extends GameProblem {
        public GameNotFoundProblem(String gameId) {
            super(gameId, "urn:joutio:game:notFound",
                    ProblemCategory.BUSINESS_RESOURCE_MISSING,
                    "Game not found",
                    "The game " + gameId + " was not found", null);
        }
    }

    class PlayerNotInGameProblem extends GameProblem {
        public PlayerNotInGameProblem(String gameId, String playerId) {
            super(gameId, "urn:joutio:game:playerNotInGame",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Player not in game",
                    "Player " + playerId + " is not a participant of game " + gameId,
                    Map.of("playerId", playerId));
        }
    }

    // ── Rounds ──

    class GameNotInProgressProblem extends GameProblem {
        public GameNotInProgressProblem(String gameId, String currentStatus) {
            super(gameId, "urn:joutio:game:notInProgress",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Game is not in progress",
                    "Game " + gameId + " is in status " + currentStatus + " but must be IN_PROGRESS",
                    Map.of("currentStatus", currentStatus));
        }
    }

    class RoundNotStartableProblem extends GameProblem {
        public RoundNotStartableProblem(String gameId, String round, String currentStatus) {
            super(gameId, "urn:joutio:game:roundNotStartable",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Round cannot be started",
                    "Round " + round + " is in status " + currentStatus + " and cannot be started",
                    Map.of("round", round, "currentStatus", currentStatus));
        }
    }

    /**
     * Une réponse a été reçue avant la révélation des réponses (round pas encore ANSWERABLE).
     */
    /**
     * Réponse hors délai : reçue après la grâce de {@code answerDeadlineAt}, ou dont le temps retenu dépasse le chrono.
     * La manche l'enregistre comme une non-réponse à son expiration.
     */
    class AnswerAfterDeadlineProblem extends GameProblem {
        public AnswerAfterDeadlineProblem(String gameId, String round, long retainedMs) {
            super(gameId, "urn:joutio:game:answerAfterDeadline",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Answer after deadline",
                    "The answer to round " + round + " in game " + gameId + " arrived after the deadline",
                    Map.of("round", round, "retainedMs", retainedMs));
        }
    }

    class RoundNotRevealedProblem extends GameProblem {
        public RoundNotRevealedProblem(String gameId, String round, String currentStatus) {
            super(gameId, "urn:joutio:game:roundNotRevealed",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Round is not answerable",
                    "Round " + round + " in game " + gameId + " is in status " + currentStatus
                            + " and cannot accept answers yet",
                    Map.of("round", round, "currentStatus", currentStatus));
        }
    }

    /**
     * La révélation a été demandée sur un round qui n'affiche pas encore sa question.
     */
    class RoundNotRevealableProblem extends GameProblem {
        public RoundNotRevealableProblem(String gameId, String round, String currentStatus) {
            super(gameId, "urn:joutio:game:roundNotRevealable",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Round cannot be revealed",
                    "Round " + round + " in game " + gameId + " is in status " + currentStatus
                            + " and cannot be revealed",
                    Map.of("round", round, "currentStatus", currentStatus));
        }
    }

    /**
     * Réponse destinée à une autre manche que la manche courante (renvoi tardif après la clôture).
     */
    class RoundMismatchProblem extends GameProblem {
        public RoundMismatchProblem(String gameId, String round, String currentRound) {
            super(gameId, "urn:joutio:game:roundMismatch",
                    ProblemCategory.BUSINESS_INVALID_COMMAND,
                    "Round is not the current one",
                    "Answer for round " + round + " in game " + gameId + " while the current round is " + currentRound,
                    Map.of("round", round, "currentRound", currentRound));
        }
    }
}
