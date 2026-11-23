package com.joutio.game.domain.model;

import com.joutio.microservice.core.domain.model.i18n.Language;
import lombok.Builder;

import java.time.Instant;
import java.util.Map;

/**
 * Round d'une partie, tel que projeté pour la revue des manches : le contenu de la question est
 * un <b>snapshot</b> figé à la création de la partie (réponses, traductions, image, difficulté) —
 * la question a pu être modifiée ou supprimée dans theme depuis.
 */
@Builder(toBuilder = true)
public record GameRound(
        GameRoundType round,
        String questionId,
        String questionText,
        GameQuestionChoice correctAnswer,
        GameQuestionChoice player1Choice,
        int player1Points,
        Long player1TimeMs,
        GameQuestionChoice player2Choice,
        int player2Points,
        Long player2TimeMs,
        GameRoundStatus status,
        Instant revealedAt,
        Instant answerDeadlineAt,
        /** Réponses (repli FR), clé = choix (A-D). */
        Map<GameQuestionChoice, String> answers,
        /** Contenus localisés (clé = langue). */
        Map<Language, GameQuestionContent> translations,
        String imageUrl,
        /** Description de l'image (repli FR). */
        String imageAlt,
        String difficulty
) {
}
