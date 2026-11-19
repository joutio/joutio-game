package com.joutio.game.domain.model;

import java.util.Map;

/**
 * Contenu localisé d'une question de duel : texte, réponses A-D et description de l'image.
 */
public record GameQuestionContent(
        String text,
        Map<GameQuestionChoice, String> answers,
        /** Description de l'image dans cette langue (lecteurs d'écran) ; {@code null} si absente. */
        String imageAlt
) {
}
