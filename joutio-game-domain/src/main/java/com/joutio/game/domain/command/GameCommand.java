package com.joutio.game.domain.command;

import com.joutio.game.domain.model.BotDifficulty;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.model.GameQuestion;
import com.joutio.game.domain.model.GameQuestionChoice;
import com.joutio.game.domain.model.GameRoundType;
import com.joutio.game.domain.model.PlayerProgressSnapshot;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.Instant;
import java.util.List;

public interface GameCommand {
    String gameId();

    /**
     * Crée une partie avec les deux joueurs déclarés. {@code player2Id} est toujours requis :
     * un duel contre le bot utilise {@code JoutioConstants.SYSTEM_USER_ID} avec
     * {@code player2Type = BOT}.
     *
     * <p>{@code questions} est préparé par l'appelant (salle, appariement ou façade) : la
     * sélection (sujet, langues, nombre) est un I/O inter-service qui ne doit pas s'exécuter
     * dans le handler de commande. L'agrégat ne fait que valider la complétude.</p>
     *
     * <p>{@code player1Progress}/{@code player2Progress} figent l'Elo des joueurs à la
     * création : l'écran de résultat l'affiche au lieu de la progression courante du compte.</p>
     */
    record CreateGameCommand(
            @TargetAggregateIdentifier String gameId,
            String topicId,
            String player1Id,
            String player1Name,
            String player2Id,
            String player2Name,
            GamePlayerType player2Type,
            BotDifficulty botDifficulty,
            PlayerProgressSnapshot player1Progress,
            PlayerProgressSnapshot player2Progress,
            List<GameQuestion> questions
    ) implements GameCommand {
    }

    /**
     * Un joueur abandonne une partie en cours : l'adversaire est déclaré vainqueur.
     */
    record ForfeitGameCommand(
            @TargetAggregateIdentifier String gameId,
            String playerId
    ) implements GameCommand {
    }

    /**
     * Clôt la partie au score (fin normale après le dernier round, ou expiration serveur).
     */
    record EndGameCommand(
            @TargetAggregateIdentifier String gameId
    ) implements GameCommand {
    }

    /**
     * Annulation système avant démarrage (expiration) — distincte de la sortie joueur.
     */
    record CancelGameCommand(
            @TargetAggregateIdentifier String gameId,
            String reason
    ) implements GameCommand {
    }

    record StartRoundCommand(
            @TargetAggregateIdentifier String gameId
    ) implements GameCommand {
    }

    /**
     * Révèle les réponses du round courant et arme le chrono. Émise par la saga après
     * la phase de lecture — jamais par le client, afin de rester autoritaire sur le temps.
     */
    record RevealQuestionCommand(
            @TargetAggregateIdentifier String gameId
    ) implements GameCommand {
    }

    /**
     * Réponse d'un joueur à la manche {@code round}. Idempotente : un renvoi (réseau instable) pour une manche à
     * laquelle le joueur a déjà répondu est sans effet ; une autre manche que la courante est refusée
     * ({@code RoundMismatchProblem}), un renvoi tardif n'atteint donc jamais la manche suivante.
     */
    record AnswerQuestionCommand(
            @TargetAggregateIdentifier String gameId,
            String playerId,
            GameRoundType round,
            GameQuestionChoice choice,
            /** Réception par le BFF. */
            Instant timestamp,
            /**
             * Temps de réflexion mesuré par le client depuis l'affichage réel des réponses (ms), {@code null} s'il
             * ne l'envoie pas : le temps serveur fait alors foi ({@link GameRules#retainedAnswerMs}).
             */
            Long clientElapsedMs
    ) implements GameCommand {
    }

    record CloseRoundCommand(
            @TargetAggregateIdentifier String gameId
    ) implements GameCommand {
    }

    /**
     * Le bot donne la réponse tirée à la révélation de {@code round} (choix et instant gardés par l'agrégat). Émise par
     * la saga à l'instant de la réponse ; sans effet si le bot a déjà répondu ou si la manche n'est plus ouverte.
     */
    record BotAnswerCommand(
            @TargetAggregateIdentifier String gameId,
            GameRoundType round
    ) implements GameCommand {
    }

    /**
     * Échéance de {@code round} : chaque réponse manquante est enregistrée, celle tirée pour le bot, vide à l'échéance
     * pour un humain. Sans effet si la manche n'est plus ouverte. Le bot ne perd donc jamais sa réponse, quel que soit
     * l'ordre d'arrivée des deadlines sous charge.
     */
    record ExpireRoundCommand(
            @TargetAggregateIdentifier String gameId,
            GameRoundType round
    ) implements GameCommand {
    }
}
