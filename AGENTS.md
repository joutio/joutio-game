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
  2. `QUESTION_REVEAL` → `RevealQuestionCommand` ;
  3. réponses (`AnswerQuestionCommand`), le bot répondant sur `BOT_ANSWERS` ;
  4. `ROUND_EXPIRED` à 10 s : une non-réponse compte comme fausse ;
  5. `CloseRoundCommand`, puis `NEXT_ROUND_STARTS`.

  La partie se termine par `EndGameCommand`.
- Score et constantes dans `GameRules` : 10/20 points, bonus de vitesse jusqu'à +10/+20.
- `ForfeitGameCommand` : abandon, l'adversaire gagne.
- `CancelGameCommand` : annulation sans vainqueur (motif `PLAYER_CANCELLED`, `GAME_EXPIRED`,
  `ACCOUNT_DELETED`). Sans effet sur une partie terminée.
- Filet `GAME_EXPIRED` (24 h) : partie non démarrée → annulée ; en cours → close au score.
- `GameEndedEvent` porte les faits utiles à la progression : réponses correctes et rapides,
  bonnes réponses sur image, `botGame`, nul. Profile ignore les gains d'un entraînement.

## Lectures fournies (`GameQuery`)

| Requête | Usage |
|---|---|
| `GetActiveGamesQuery(playerId)` | parties `IN_PROGRESS`, liste vide si aucune |
| `GetPlayerGamesQuery(playerId, topicId, opponentId, opponentType, outcome, sort, page, size)` | historique filtré, `outcome` relatif au joueur interrogé |
| `GetPopularTopicsQuery(since, limit)` | sujets les plus joués sur une fenêtre |
| `GetGameEventsQuery(gameId)` | événements de la partie en `EventEnvelope` (via `GameEventStorePort` et son adaptateur) ; le mapping web est fait par le BFF |
| `GetGameResultQuery(gameId, playerId)` | composition du résultat |
| `GetPlayedQuestionIdsQuery(topicId, playerIds)` | questions déjà affichées aux joueurs (préparation de salle) |

Événements consommés ailleurs : `GameCreatedEvent` (theme, BFF), `QuestionAnsweredEvent`
(difficulté dans theme), `GameEndedEvent` (profile, leaderboard, matchmaking).

## Commandes

```bash
mvn -q -pl joutio-game-domain test
mvn -pl joutio-game-infrastructure test
```

## Pièges

- `GameFlowSaga` utilise un `Random` statique (réponses du bot) et `Instant.now()` pour certains
  délais : non déterministe au replay (`docs/ROADMAP.md`, Dette).
- 3 deadlines par manche : c'est le principal coût sous charge (`stress-test/INFRA.md`).
