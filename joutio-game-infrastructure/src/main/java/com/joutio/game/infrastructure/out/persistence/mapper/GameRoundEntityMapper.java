package com.joutio.game.infrastructure.out.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joutio.game.domain.model.GameQuestionChoice;
import com.joutio.game.domain.model.GameQuestionContent;
import com.joutio.game.domain.model.GameRound;
import com.joutio.game.infrastructure.out.persistence.entity.GameEntity;
import com.joutio.game.infrastructure.out.persistence.entity.GameRoundEntity;
import com.joutio.microservice.core.domain.model.i18n.Language;

import java.util.Map;

public final class GameRoundEntityMapper {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<GameQuestionChoice, String>> ANSWERS_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<Language, GameQuestionContent>> TRANSLATIONS_TYPE = new TypeReference<>() {};

    private GameRoundEntityMapper() {
    }

    public static GameRound toDomain(GameRoundEntity entity) {
        return GameRound.builder()
                .round(entity.getRound())
                .questionId(entity.getQuestionId())
                .questionText(entity.getQuestionText())
                .correctAnswer(entity.getCorrectAnswer())
                .player1Choice(entity.getPlayer1Choice())
                .player1Points(entity.getPlayer1Points())
                .player1TimeMs(entity.getPlayer1TimeMs())
                .player2Choice(entity.getPlayer2Choice())
                .player2Points(entity.getPlayer2Points())
                .player2TimeMs(entity.getPlayer2TimeMs())
                .status(entity.getStatus())
                .revealedAt(entity.getRevealedAt())
                .answerDeadlineAt(entity.getAnswerDeadlineAt())
                .answers(readJson(entity.getAnswersJson(), ANSWERS_TYPE))
                .translations(readJson(entity.getTranslationsJson(), TRANSLATIONS_TYPE))
                .imageUrl(entity.getImageUrl())
                .imageAlt(entity.getImageAlt())
                .difficulty(entity.getDifficulty())
                .build();
    }

    public static GameRoundEntity toEntity(GameRound round, GameEntity gameEntity) {
        GameRoundEntity entity = new GameRoundEntity();
        entity.setId(gameEntity.getGameId() + "-" + round.round().name());
        entity.setGame(gameEntity);
        entity.setRound(round.round());
        entity.setQuestionId(round.questionId());
        entity.setQuestionText(round.questionText());
        entity.setCorrectAnswer(round.correctAnswer());
        entity.setPlayer1Choice(round.player1Choice());
        entity.setPlayer1Points(round.player1Points());
        entity.setPlayer1TimeMs(round.player1TimeMs());
        entity.setPlayer2Choice(round.player2Choice());
        entity.setPlayer2Points(round.player2Points());
        entity.setPlayer2TimeMs(round.player2TimeMs());
        entity.setStatus(round.status());
        entity.setRevealedAt(round.revealedAt());
        entity.setAnswerDeadlineAt(round.answerDeadlineAt());
        entity.setAnswersJson(writeJson(round.answers()));
        entity.setTranslationsJson(writeJson(round.translations()));
        entity.setImageUrl(round.imageUrl());
        entity.setImageAlt(round.imageAlt());
        entity.setDifficulty(round.difficulty());
        return entity;
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("Impossible de sérialiser le snapshot de round", error);
        }
    }

    private static <T> T readJson(String json, TypeReference<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("Impossible de désérialiser le snapshot de round", error);
        }
    }
}
