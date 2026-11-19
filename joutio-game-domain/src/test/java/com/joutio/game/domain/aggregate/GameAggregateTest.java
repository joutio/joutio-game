package com.joutio.game.domain.aggregate;

import com.joutio.axon.test.JoutioAxonMatchers;
import com.joutio.game.domain.command.GameCommand;
import com.joutio.game.domain.event.GameEvent;
import com.joutio.game.domain.exception.GameExceptions;
import com.joutio.game.domain.model.BotAnswer;
import com.joutio.game.domain.model.BotDifficulty;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.model.GameQuestion;
import com.joutio.game.domain.model.GameQuestionChoice;
import com.joutio.game.domain.model.GameQuestionContent;
import com.joutio.game.domain.model.GameRoundType;
import com.joutio.game.domain.model.GameRules;
import com.joutio.game.domain.model.PlayerProgressSnapshot;
import com.joutio.microservice.core.domain.model.i18n.Language;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Test Axon in-memory de l'agrégat {@link GameAggregate} via {@link AggregateTestFixture}.
 *
 * <p>100 % in-memory : event store de l'agrégat en mémoire, aucun Postgres ni Axon Server.
 * Les questions sont portées par la commande (aucun port sortant). La partie démarre immédiatement
 * à la création (émission de {@code GameCreatedEvent} puis {@code GameStartedEvent}).</p>
 */
class GameAggregateTest {

    private static final String GAME_ID = "game-1";
    private static final String TOPIC_ID = "topic-1";
    private static final String PLAYER_1 = "player-1";
    private static final String PLAYER_2 = "player-2";
    private static final PlayerProgressSnapshot PLAYER_1_PROGRESS = new PlayerProgressSnapshot(220);
    private static final PlayerProgressSnapshot PLAYER_2_PROGRESS = new PlayerProgressSnapshot(200);

    private final AggregateTestFixture<GameAggregate> fixture =
            new AggregateTestFixture<>(GameAggregate.class);

    @BeforeEach
    void setUp() {
        fixture.setReportIllegalStateChange(false);
    }

    @Test
    void createGame_appliesCreatedThenStarted() {
        fixture.givenNoPriorActivity()
                .when(createCommand())
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        GameEvent.GameCreatedEvent.class,
                        e -> ((GameEvent.GameCreatedEvent) e).gameId().equals(GAME_ID)))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        GameEvent.GameStartedEvent.class,
                        e -> ((GameEvent.GameStartedEvent) e).gameId().equals(GAME_ID)));
    }

    @Test
    void createGame_carriesPlayerProgressSnapshot() {
        fixture.givenNoPriorActivity()
                .when(createCommand())
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        GameEvent.GameCreatedEvent.class,
                        e -> {
                            GameEvent.GameCreatedEvent created = (GameEvent.GameCreatedEvent) e;
                            return PLAYER_1_PROGRESS.equals(created.player1Progress())
                                    && PLAYER_2_PROGRESS.equals(created.player2Progress());
                        }));
    }

    @Test
    void createGame_withFewerThanSevenQuestions_isRejected() {
        GameCommand.CreateGameCommand command = new GameCommand.CreateGameCommand(
                GAME_ID, TOPIC_ID, PLAYER_1, "Alpha", PLAYER_2, "Bravo",
                GamePlayerType.HUMAN, null,
                PLAYER_1_PROGRESS, PLAYER_2_PROGRESS,
                questions().subList(0, GameRules.TOTAL_ROUNDS - 1));

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(GameExceptions.NotEnoughQuestionsProblem.class);
    }

    @Test
    void createGame_withBlankPlayer2_isRejected() {
        GameCommand.CreateGameCommand command = new GameCommand.CreateGameCommand(
                GAME_ID, TOPIC_ID, PLAYER_1, "Alpha", " ", "Bravo",
                GamePlayerType.HUMAN, null,
                PLAYER_1_PROGRESS, PLAYER_2_PROGRESS, questions());

        fixture.givenNoPriorActivity()
                .when(command)
                .expectException(GameExceptions.MissingPlayerProblem.class);
    }

    @Test
    void answerBeforeReveal_isRejected() {
        Instant shownAt = Instant.now();

        fixture.given(concat(created(), roundStarted(shownAt)))
                .when(new GameCommand.AnswerQuestionCommand(
                        GAME_ID, PLAYER_1, GameRoundType.ROUND_1, GameQuestionChoice.A, shownAt.plusMillis(500)))
                .expectException(GameExceptions.RoundNotRevealedProblem.class);
    }

    @Test
    void revealBeforeQuestionShown_isRejected() {
        fixture.given(created())
                .when(new GameCommand.RevealQuestionCommand(GAME_ID))
                .expectException(GameExceptions.RoundNotRevealableProblem.class);
    }

    @Test
    void revealQuestion_appliesQuestionRevealedEvent() {
        Instant shownAt = Instant.now();

        fixture.given(concat(created(), roundStarted(shownAt)))
                .when(new GameCommand.RevealQuestionCommand(GAME_ID))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        GameEvent.QuestionRevealedEvent.class,
                        e -> ((GameEvent.QuestionRevealedEvent) e).round() == GameRoundType.ROUND_1));
    }

    @Test
    void revealQuestion_humanGame_drawsNoBotAnswer() {
        fixture.given(concat(created(), roundStarted(Instant.now())))
                .when(new GameCommand.RevealQuestionCommand(GAME_ID))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        GameEvent.QuestionRevealedEvent.class,
                        e -> ((GameEvent.QuestionRevealedEvent) e).botAnswer() == null));
    }

    @Test
    void revealQuestion_botGame_carriesBotAnswerWithinDifficultyWindow() {
        BotDifficulty difficulty = BotDifficulty.HARD;

        fixture.given(concat(created(GamePlayerType.BOT, difficulty), roundStarted(Instant.now())))
                .when(new GameCommand.RevealQuestionCommand(GAME_ID))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        GameEvent.QuestionRevealedEvent.class,
                        e -> {
                            GameEvent.QuestionRevealedEvent revealed = (GameEvent.QuestionRevealedEvent) e;
                            BotAnswer bot = revealed.botAnswer();
                            long delay = Duration.between(revealed.revealedAt(), bot.answerAt()).toMillis();
                            return bot.choice() != null
                                    && delay >= difficulty.minAnswerDelayMillis()
                                    && delay <= difficulty.maxAnswerDelayMillis();
                        }));
    }

    @Test
    void forfeitEndsGame_andOpponentWins() {
        fixture.given(created())
                .when(new GameCommand.ForfeitGameCommand(GAME_ID, PLAYER_2))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        GameEvent.GameEndedEvent.class,
                        e -> {
                            GameEvent.GameEndedEvent ended = (GameEvent.GameEndedEvent) e;
                            return PLAYER_1.equals(ended.winnerId());
                        }));
    }

    @Test
    void forfeitByNonPlayer_isRejected() {
        fixture.given(created())
                .when(new GameCommand.ForfeitGameCommand(GAME_ID, "stranger"))
                .expectException(GameExceptions.PlayerNotInGameProblem.class);
    }

    @Test
    void forfeit_whenNotInProgress_isRejected() {
        fixture.given(finished())
                .when(new GameCommand.ForfeitGameCommand(GAME_ID, PLAYER_1))
                .expectException(GameExceptions.GameNotInProgressProblem.class);
    }

    @Test
    void answerAfterReveal_scoresFromRevealAndReportsCorrect() {
        Instant shownAt = Instant.now();
        Instant revealedAt = shownAt.plusMillis(GameRules.QUESTION_REVEAL_MS);
        Instant answeredAt = revealedAt.plusSeconds(2);

        fixture.given(concat(created(), roundStarted(shownAt), revealed(revealedAt)))
                .when(new GameCommand.AnswerQuestionCommand(
                        GAME_ID, PLAYER_1, GameRoundType.ROUND_1, GameQuestionChoice.A, answeredAt))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        GameEvent.QuestionAnsweredEvent.class,
                        e -> {
                            GameEvent.QuestionAnsweredEvent answered = (GameEvent.QuestionAnsweredEvent) e;
                            return answered.correct()
                                    && answered.pointsEarned() == 18
                                    && answered.timeMs() == 2000;
                        }));
    }

    /** Requête perdue puis renvoyée : la réponse déjà enregistrée n'est pas rejouée ni refusée. */
    @Test
    void answer_resentForAnAlreadyAnsweredRound_isIgnored() {
        Instant shownAt = Instant.now();
        Instant revealedAt = shownAt.plusMillis(GameRules.QUESTION_REVEAL_MS);

        fixture.given(concat(created(), roundStarted(shownAt), revealed(revealedAt),
                        new GameEvent.QuestionAnsweredEvent(GAME_ID, GameRoundType.ROUND_1, questions().get(0).questionId(),
                                PLAYER_1, GamePlayerType.HUMAN, GameQuestionChoice.A, true, revealedAt.plusSeconds(2), 18, 2000)))
                .when(new GameCommand.AnswerQuestionCommand(
                        GAME_ID, PLAYER_1, GameRoundType.ROUND_1, GameQuestionChoice.A, revealedAt.plusSeconds(3)))
                .expectNoEvents();
    }

    /** Renvoi tardif d'une réponse de la manche précédente : jamais appliqué à la manche courante. */
    @Test
    void answer_forAnotherRound_isRejected() {
        Instant shownAt = Instant.now();

        fixture.given(concat(created(), roundStarted(shownAt), revealed(shownAt.plusMillis(GameRules.QUESTION_REVEAL_MS))))
                .when(new GameCommand.AnswerQuestionCommand(
                        GAME_ID, PLAYER_1, GameRoundType.ROUND_2, GameQuestionChoice.A, shownAt.plusSeconds(4)))
                .expectException(GameExceptions.RoundMismatchProblem.class);
    }

    /** Échéance avant la réponse du bot (deadline en retard sous charge) : le bot garde la réponse tirée. */
    @Test
    void expireRound_botGame_recordsTheDrawnBotAnswerAndAnEmptyHumanAnswer() {
        Instant shownAt = Instant.now();
        Instant revealedAt = shownAt.plusMillis(GameRules.QUESTION_REVEAL_MS);
        BotAnswer bot = new BotAnswer(GameQuestionChoice.A, revealedAt.plusSeconds(3));
        GameEvent.QuestionRevealedEvent revealed = revealedWithBot(revealedAt, bot);

        fixture.given(concat(created(GamePlayerType.BOT, BotDifficulty.NORMAL), roundStarted(shownAt), revealed))
                .when(new GameCommand.ExpireRoundCommand(GAME_ID, GameRoundType.ROUND_1))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        GameEvent.QuestionAnsweredEvent.class,
                        e -> {
                            GameEvent.QuestionAnsweredEvent answered = (GameEvent.QuestionAnsweredEvent) e;
                            return answered.playerId().equals(PLAYER_2)
                                    && answered.choice() == GameQuestionChoice.A
                                    && answered.correct()
                                    && answered.answeredAt().equals(bot.answerAt());
                        }))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        GameEvent.QuestionAnsweredEvent.class,
                        e -> {
                            GameEvent.QuestionAnsweredEvent answered = (GameEvent.QuestionAnsweredEvent) e;
                            return answered.playerId().equals(PLAYER_1)
                                    && answered.choice() == null
                                    && answered.answeredAt().equals(revealed.answerDeadlineAt());
                        }));
    }

    @Test
    void botAnswer_recordsTheDrawnChoiceAtItsInstant() {
        Instant shownAt = Instant.now();
        Instant revealedAt = shownAt.plusMillis(GameRules.QUESTION_REVEAL_MS);
        BotAnswer bot = new BotAnswer(GameQuestionChoice.B, revealedAt.plusSeconds(2));

        fixture.given(concat(created(GamePlayerType.BOT, BotDifficulty.NORMAL), roundStarted(shownAt), revealedWithBot(revealedAt, bot)))
                .when(new GameCommand.BotAnswerCommand(GAME_ID, GameRoundType.ROUND_1))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        GameEvent.QuestionAnsweredEvent.class,
                        e -> {
                            GameEvent.QuestionAnsweredEvent answered = (GameEvent.QuestionAnsweredEvent) e;
                            return answered.playerId().equals(PLAYER_2)
                                    && answered.choice() == GameQuestionChoice.B
                                    && !answered.correct()
                                    && answered.timeMs() == 2000;
                        }));
    }

    @Test
    void botAnswer_onceTheBotHasAnswered_isIgnored() {
        Instant shownAt = Instant.now();
        Instant revealedAt = shownAt.plusMillis(GameRules.QUESTION_REVEAL_MS);
        BotAnswer bot = new BotAnswer(GameQuestionChoice.B, revealedAt.plusSeconds(2));

        fixture.given(concat(created(GamePlayerType.BOT, BotDifficulty.NORMAL), roundStarted(shownAt), revealedWithBot(revealedAt, bot),
                        new GameEvent.QuestionAnsweredEvent(GAME_ID, GameRoundType.ROUND_1, questions().get(0).questionId(),
                                PLAYER_2, GamePlayerType.BOT, GameQuestionChoice.B, false, bot.answerAt(), 0, 2000)))
                .when(new GameCommand.BotAnswerCommand(GAME_ID, GameRoundType.ROUND_1))
                .expectNoEvents();
    }

    @Test
    void expireRound_forARoundNoLongerCurrent_isIgnored() {
        Instant shownAt = Instant.now();
        fixture.given(concat(created(), roundStarted(shownAt), revealed(shownAt.plusMillis(GameRules.QUESTION_REVEAL_MS))))
                .when(new GameCommand.ExpireRoundCommand(GAME_ID, GameRoundType.ROUND_2))
                .expectNoEvents();
    }

    // ── Fixtures ──

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] result = new Object[base.length + extra.length];
        System.arraycopy(base, 0, result, 0, base.length);
        System.arraycopy(extra, 0, result, base.length, extra.length);
        return result;
    }

    private GameCommand.CreateGameCommand createCommand() {
        return new GameCommand.CreateGameCommand(
                GAME_ID, TOPIC_ID, PLAYER_1, "Alpha", PLAYER_2, "Bravo",
                GamePlayerType.HUMAN, null,
                PLAYER_1_PROGRESS, PLAYER_2_PROGRESS, questions());
    }

    /** La partie naît démarrée : {@code GameCreatedEvent} puis {@code GameStartedEvent}. */
    private Object[] created() {
        return created(GamePlayerType.HUMAN, null);
    }

    private Object[] created(GamePlayerType player2Type, BotDifficulty botDifficulty) {
        Instant now = Instant.now();
        return new Object[]{
                new GameEvent.GameCreatedEvent(
                        GAME_ID, TOPIC_ID, PLAYER_1, "Alpha", PLAYER_2, "Bravo",
                        player2Type, questions(), botDifficulty,
                        PLAYER_1_PROGRESS, PLAYER_2_PROGRESS, now),
                new GameEvent.GameStartedEvent(GAME_ID, now, now.plusMillis(GameRules.MATCH_INTRO_MS))
        };
    }

    private Object[] finished() {
        return concat(created(), ended());
    }

    private GameEvent.GameEndedEvent ended() {
        return new GameEvent.GameEndedEvent(
                GAME_ID,
                PLAYER_1,
                PLAYER_1, "Alpha",
                PLAYER_2, "Bravo",
                TOPIC_ID,
                20, 10,
                2, 1,
                1, 0,
                0, 0,
                Instant.now());
    }

    private GameEvent.RoundStartedEvent roundStarted(Instant shownAt) {
        return new GameEvent.RoundStartedEvent(
                GAME_ID,
                GameRoundType.ROUND_1,
                questions().get(0),
                shownAt,
                shownAt.plusMillis(GameRules.QUESTION_REVEAL_MS)
        );
    }

    @Test
    void endGame_countsCorrectAnswersOnImageQuestions() {
        Instant shownAt = Instant.now();
        Instant revealedAt = shownAt.plusMillis(GameRules.QUESTION_REVEAL_MS);
        Instant answeredAt = revealedAt.plusSeconds(2);

        fixture.given(concat(createdWithImages(), roundStarted(shownAt), revealed(revealedAt),
                        answeredCorrectlyByPlayer1(answeredAt)))
                .when(new GameCommand.EndGameCommand(GAME_ID))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        GameEvent.GameEndedEvent.class,
                        e -> {
                            GameEvent.GameEndedEvent ended = (GameEvent.GameEndedEvent) e;
                            return ended.player1ImageCorrectAnswers() == 1
                                    && ended.player2ImageCorrectAnswers() == 0;
                        }));
    }

    @Test
    void endGame_ignoresImageCountingWithoutIllustration() {
        Instant shownAt = Instant.now();
        Instant revealedAt = shownAt.plusMillis(GameRules.QUESTION_REVEAL_MS);
        Instant answeredAt = revealedAt.plusSeconds(2);

        fixture.given(concat(created(), roundStarted(shownAt), revealed(revealedAt),
                        answeredCorrectlyByPlayer1(answeredAt)))
                .when(new GameCommand.EndGameCommand(GAME_ID))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        GameEvent.GameEndedEvent.class,
                        e -> {
                            GameEvent.GameEndedEvent ended = (GameEvent.GameEndedEvent) e;
                            return ended.player1ImageCorrectAnswers() == 0
                                    && ended.player1CorrectAnswers() == 1;
                        }));
    }

    private GameEvent.QuestionRevealedEvent revealed(Instant revealedAt) {
        return revealedWithBot(revealedAt, null);
    }

    private GameEvent.QuestionRevealedEvent revealedWithBot(Instant revealedAt, BotAnswer botAnswer) {
        return new GameEvent.QuestionRevealedEvent(
                GAME_ID,
                GameRoundType.ROUND_1,
                revealedAt,
                revealedAt.plusSeconds(GameRules.ROUND_TIMEOUT_SECONDS),
                botAnswer
        );
    }

    /** Même partie, mais toutes les questions sont **illustrées**. */
    private Object[] createdWithImages() {
        Instant now = Instant.now();
        return new Object[]{
                new GameEvent.GameCreatedEvent(
                        GAME_ID, TOPIC_ID, PLAYER_1, "Alpha", PLAYER_2, "Bravo",
                        GamePlayerType.HUMAN, imageQuestions(), null,
                        PLAYER_1_PROGRESS, PLAYER_2_PROGRESS, now),
                new GameEvent.GameStartedEvent(GAME_ID, now, now.plusMillis(GameRules.MATCH_INTRO_MS))
        };
    }

    private static List<GameQuestion> imageQuestions() {
        return questions().stream()
                .map(question -> new GameQuestion(
                        question.questionId(),
                        question.translations(),
                        "https://media.joutio.com/wikipedia/illustration.png",
                        question.difficulty(),
                        question.correctAnswer()))
                .toList();
    }

    private static GameEvent.QuestionAnsweredEvent answeredCorrectlyByPlayer1(Instant answeredAt) {
        return new GameEvent.QuestionAnsweredEvent(
                GAME_ID, GameRoundType.ROUND_1, "question-0", PLAYER_1,
                GamePlayerType.HUMAN, GameQuestionChoice.A, true, answeredAt, 10, 2000);
    }

    private static List<GameQuestion> questions() {
        return IntStream.range(0, GameRules.TOTAL_ROUNDS)
                .mapToObj(i -> new GameQuestion(
                        "question-" + i,
                        Map.of(
                                Language.FR, new GameQuestionContent(
                                        "Question " + i,
                                        Map.of(
                                                GameQuestionChoice.A, "Réponse A",
                                                GameQuestionChoice.B, "Réponse B",
                                                GameQuestionChoice.C, "Réponse C",
                                                GameQuestionChoice.D, "Réponse D")),
                                Language.EN, new GameQuestionContent(
                                        "Question " + i,
                                        Map.of(
                                                GameQuestionChoice.A, "Answer A",
                                                GameQuestionChoice.B, "Answer B",
                                                GameQuestionChoice.C, "Answer C",
                                                GameQuestionChoice.D, "Answer D"))),
                        null,
                        null,
                        GameQuestionChoice.A))
                .toList();
    }
}
