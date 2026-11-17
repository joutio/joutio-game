package com.joutio.game.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotAnswerTest {

    private static final Instant REVEALED_AT = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    void draw_staysWithinTheDifficultyDelayWindow() {
        SplittableRandom random = new SplittableRandom(7);
        for (BotDifficulty difficulty : BotDifficulty.values()) {
            for (int i = 0; i < 500; i++) {
                BotAnswer answer = BotAnswer.draw(difficulty, GameQuestionChoice.A, REVEALED_AT, random);
                long delay = Duration.between(REVEALED_AT, answer.answerAt()).toMillis();
                assertTrue(delay >= difficulty.minAnswerDelayMillis() && delay <= difficulty.maxAnswerDelayMillis(), difficulty + " : " + delay);
            }
        }
    }

    @Test
    void draw_isReproducibleForTheSameSeed() {
        BotAnswer first = BotAnswer.draw(BotDifficulty.NORMAL, GameQuestionChoice.B, REVEALED_AT, new SplittableRandom(42));
        BotAnswer second = BotAnswer.draw(BotDifficulty.NORMAL, GameQuestionChoice.B, REVEALED_AT, new SplittableRandom(42));
        assertEquals(first, second);
    }

    @Test
    void draw_answersCorrectlyAboutAsOftenAsTheDifficultySays() {
        SplittableRandom random = new SplittableRandom(3);
        int draws = 4000;
        long correct = 0;
        for (int i = 0; i < draws; i++) {
            if (BotAnswer.draw(BotDifficulty.HARD, GameQuestionChoice.C, REVEALED_AT, random).choice() == GameQuestionChoice.C) {
                correct++;
            }
        }
        double rate = (double) correct / draws;
        assertTrue(rate >= 0.80 && rate <= 0.90, "taux : " + rate);
    }
}
