package com.joutio.game.domain.model;

import java.time.Instant;

public record PlayerAnswer(
        GamePlayer player,
        GameQuestionChoice choice,
        boolean correct,
        int pointsEarned,
        Instant answeredAt
) {
}
