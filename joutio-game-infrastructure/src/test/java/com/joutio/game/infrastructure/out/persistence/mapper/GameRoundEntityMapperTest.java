package com.joutio.game.infrastructure.out.persistence.mapper;

import com.joutio.game.domain.model.GameQuestionChoice;
import com.joutio.game.domain.model.GameQuestionContent;
import com.joutio.game.domain.model.GameRound;
import com.joutio.game.domain.model.GameRoundStatus;
import com.joutio.game.domain.model.GameRoundType;
import com.joutio.game.infrastructure.out.persistence.entity.GameEntity;
import com.joutio.game.infrastructure.out.persistence.entity.GameRoundEntity;
import com.joutio.microservice.core.domain.model.i18n.Language;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Le snapshot de question est sérialisé en JSONB : le round-trip domaine ↔ entité doit préserver
 * les réponses et les traductions (clés enum → chaînes).
 */
class GameRoundEntityMapperTest {

    @Test
    void roundTripsQuestionSnapshot() {
        GameRound round = GameRound.builder()
                .round(GameRoundType.ROUND_7)
                .questionId("q-1")
                .questionText("Quelle est la capitale de la France ?")
                .correctAnswer(GameQuestionChoice.A)
                .status(GameRoundStatus.CLOSED)
                .answers(Map.of(
                        GameQuestionChoice.A, "Paris",
                        GameQuestionChoice.B, "Londres",
                        GameQuestionChoice.C, "Berlin",
                        GameQuestionChoice.D, "Madrid"))
                .translations(Map.of(
                        Language.EN, new GameQuestionContent(
                                "What is the capital of France?",
                                Map.of(GameQuestionChoice.A, "Paris", GameQuestionChoice.B, "London"),
                                "A photo of Paris"),
                        Language.FR, new GameQuestionContent(
                                "Quelle est la capitale de la France ?",
                                Map.of(GameQuestionChoice.A, "Paris", GameQuestionChoice.B, "Londres"),
                                "Une photo de Paris")))
                .imageUrl("https://img.example/paris.jpg")
                .imageAlt("Une photo de Paris")
                .difficulty("HARD")
                .build();

        GameEntity game = new GameEntity();
        game.setGameId("game-1");

        GameRoundEntity entity = GameRoundEntityMapper.toEntity(round, game);
        GameRound restored = GameRoundEntityMapper.toDomain(entity);

        assertEquals(round, restored);
    }
}
