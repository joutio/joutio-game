package com.joutio.game.domain.model;

import java.util.Map;

/**
 * Contenu localisé d'une question de duel (texte + réponses A-D).
 */
public record GameQuestionContent(
        String text,
        Map<GameQuestionChoice, String> answers
) {
}
