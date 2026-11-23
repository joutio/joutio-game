# AGENTS.md — joutio-game

Parties en duel à deux joueurs (humain ou bot) : manches, réponses, score, abandon, fin,
annulation. Package `com.joutio.game`, base `joutio_game`, port local 8082. Service headless.

Lire : `../../AGENTS.md`, [`docs/backend/axon.md`](../../docs/backend/axon.md), règles du duel dans
[`docs/produit/vision.md`](../../docs/produit/vision.md#le-duel), exposition dans
[`docs/api/parties.md`](../../docs/api/parties.md).

## Périmètre

- `GameAggregate` (avec cache d'agrégats Axon). Une partie est **créée par la salle**
  (matchmaking) : la salle garantit la présence des deux joueurs et **prépare** les questions.
- `CreateGameCommand` porte :
  - les 7 `GameQuestion`, avec tous leurs contenus localisés ;
  - un snapshot d'Elo par joueur (`PlayerProgressSnapshot` ; le bot reçoit l'Elo de départ).

  Le mapping `theme.Question → GameQuestion` est fait par l'émetteur. Moins de 7 questions →
  `NotEnoughQuestionsProblem`.
- Le bot est le compte système (`JoutioConstants.SYSTEM_USER_ID`).
- La revanche n'est pas ici : c'est un défi nominatif (matchmaking).
- Aucune dépendance sortante.

## Déroulé (`GameFlowSaga`)

- `MATCH_INTRO`, puis pour chaque manche :
  1. `StartRoundCommand` ;
  2. `QUESTION_REVEAL` → `RevealQuestionCommand` : `answerDeadlineAt = revealedAt` + 860 ms d'apparition des réponses
     (`ANSWERS_APPEAR_MS`, égale à celle de `@joutio/client`) + 10 s de chrono (`ANSWER_WINDOW_MS`). Les réponses se
     cliquent dès leur entrée ; le chrono affiché ne part qu'à la fin de l'apparition ;
  3. réponses (`AnswerQuestionCommand(round)`, idempotente : un renvoi pour une manche déjà répondue est sans effet,
     une autre manche → `RoundMismatchProblem`).
     **Temps retenu** (`GameRules.retainedAnswerMs`) : `clientElapsedMs` (mesuré par le client depuis l'affichage des
     réponses) borné entre 250 ms et le temps serveur (révélation → réception par le BFF) ; sans lui, le temps
     serveur. La latence montante ne coûte rien au joueur et une triche ne gagne pas plus que sa latence. Réponse reçue
     après `answerDeadlineAt` + 1 s (`ANSWER_GRACE_MS`), ou dont le temps retenu dépasse `ANSWER_WINDOW_MS` →
     `AnswerAfterDeadlineProblem`. Le choix et l'instant du bot sont tirés par l'agrégat à la révélation
     (`BotAnswer` dans `QuestionRevealedEvent`) et gardés par la manche ; `BOT_ANSWERS` envoie
     `BotAnswerCommand(round)` ;
  4. `ROUND_EXPIRED` à `answerDeadlineAt` + la grâce d'1 s envoie `ExpireRoundCommand(round)` : l'agrégat enregistre chaque réponse manquante, celle
     du bot telle que tirée, une non-réponse humaine comptant comme fausse. Ces deux commandes sont idempotentes et
     sans effet hors de la manche courante : une deadline en retard ne fait plus perdre la réponse du bot ;
  5. `CloseRoundCommand`, puis `NEXT_ROUND_STARTS`.

  La partie se termine par `EndGameCommand`.
- Score et constantes dans `GameRules` : 10/20 points, bonus de vitesse jusqu'à +10/+20.
- `ForfeitGameCommand` : abandon, l'adversaire gagne.
- `CancelGameCommand` : annulation sans vainqueur (motif `PLAYER_CANCELLED`, `GAME_EXPIRED`,
  `GAME_STALLED`, `ACCOUNT_DELETED`). Sans effet sur une partie terminée.
- Filet `GAME_EXPIRED` (24 h) : partie non démarrée → annulée ; en cours → close au score.
- Filet `GameStallSweeper` (tâche récurrente db-scheduler, 1 min, une exécution dans le cluster) :
  partie en cours sans échéance de manche depuis 2 min (deadline en mémoire perdue avec son pod) →
  `CancelGameCommand` motif `GAME_STALLED`.
- `GameEndedEvent` porte les faits utiles à la progression : réponses correctes et rapides,
  bonnes réponses sur image, `botGame`, nul. Profile ignore les gains d'un entraînement.

## Lectures fournies (`GameQuery`)

| Requête | Usage |
|---|---|
| `GetActiveGamesQuery(playerId)` | parties `IN_PROGRESS`, liste vide si aucune |
| `GetPlayerGamesQuery(playerId, topicId, opponentId, opponentType, outcome, sort, page, size)` | historique filtré, `outcome` relatif au joueur interrogé |
| `GetPopularTopicsQuery(since, limit)` | sujets les plus joués sur une fenêtre |
| `GetGameEventsQuery(gameId)` | événements de la partie en `EventEnvelope` (via `GameEventStorePort` et son adaptateur) ; le mapping web est fait par le BFF |
| `GetGameResultQuery(gameId, playerId)` | composition du résultat, manches closes comprises : la projection `round_entry` porte un **snapshot** de question (`answers`, `translations`, `image_url`, `image_alt`, `difficulty`) figé à la création — la revue montre la question telle qu'elle a été jouée, pas son état actuel dans theme |
| `GetPlayedQuestionIdsQuery(topicId, playerIds)` | questions déjà affichées aux joueurs (préparation de salle) |

Événements consommés ailleurs : `GameCreatedEvent` (theme, BFF), `QuestionAnsweredEvent`
(difficulté dans theme), `GameEndedEvent` (profile, leaderboard, matchmaking).

## Commandes

```bash
mvn -q -pl joutio-game-domain test
mvn -pl joutio-game-infrastructure test
```

## Pièges

- `GameFlowSaga` reste déterministe : deadlines planifiées aux instants des événements ; réponses vides (datées de
  `answerDeadlineAt`) et réponse du bot appliquées par l'agrégat, jamais composées par la saga. Tout aléa se tire dans l'agrégat et passe par un événement.
- 3 deadlines par manche : c'est le principal coût sous charge (`stress-test/INFRA.md`).
- Deux répliques : `game-flow-saga` et `game-projection` sont segmentés (`application.yml`,
  [axon](../../docs/backend/axon.md#segments-et-répliques)). Le cache d'agrégats est propre au pod ;
  après un changement de topologie, un agrégat périmé échoue une fois puis Axon l'évince.
