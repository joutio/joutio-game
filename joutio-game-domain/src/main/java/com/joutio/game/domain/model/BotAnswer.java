package com.joutio.game.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.random.RandomGenerator;

/**
 * Réponse du bot à une manche : tirée par l'agrégat à la révélation et portée par
 * {@code QuestionRevealedEvent}, pour que la saga la rejoue telle quelle (aucun aléa ni horloge au replay).
 *
 * @param choice   choix du bot (juste avec la probabilité de sa difficulté)
 * @param answerAt instant de la réponse, dans la fenêtre de délai de sa difficulté après la révélation
 */
public record BotAnswer(GameQuestionChoice choice, Instant answerAt) {

    public static BotAnswer draw(BotDifficulty difficulty, GameQuestionChoice correct, Instant revealedAt, RandomGenerator random) {
        GameQuestionChoice choice = random.nextDouble() < difficulty.correctProbability()
                ? correct
                : wrongAnswer(correct, random);
        long delay = random.nextLong(difficulty.minAnswerDelayMillis(), difficulty.maxAnswerDelayMillis() + 1);
        return new BotAnswer(choice, revealedAt.plus(Duration.ofMillis(delay)));
    }

    private static GameQuestionChoice wrongAnswer(GameQuestionChoice correct, RandomGenerator random) {
        GameQuestionChoice[] wrong = Arrays.stream(GameQuestionChoice.values())
                .filter(choice -> choice != correct)
                .toArray(GameQuestionChoice[]::new);
        return wrong[random.nextInt(wrong.length)];
    }
}
