package org.codingame;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.codingame.dto.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CgAnalyzeMatch {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String API_FIND_LAST_BATTLE = "https://www.codingame.com/services/gamesPlayersRanking/findLastBattlesByTestSessionHandle";
    private static final String API_FIND_GAME = "https://www.codingame.com/services/gameResultRemoteService/findByGameId";

    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) throws JsonProcessingException {
        // sessionHandle
        if (args.length < 1) {
            System.out.println("Usage: java -jar cg-analyze-match.jar <sessionHandle> [-l] [-w] [-s <searchTerm>]");
            return;
        }
        String sessionHandle = args[0];

        boolean listLostGame = false;
        boolean withWarning = false;
        String searchTerm = null;
        if (args[0].length() > 1) {
            for (int i = 1; i < args.length; i++) {
                if ("-l".equals(args[i])) {
                    listLostGame = true;
                } else if ("-w".equals(args[i])) {
                    withWarning = true;
                } else if ("-s".equals(args[i])) {
                    searchTerm = args[i + 1];
                }
            }
        }

        List<Battle> batailles = getLastGames(sessionHandle);
        if (batailles == null || batailles.isEmpty() || batailles.stream().anyMatch(battle -> battle.getGame() == null)) {
            System.err.println("\u27A4 Aucun historique récupéré pour le sessionHandle " + sessionHandle);
            return;
        }
        Player player = batailles.getFirst().getPlayers().stream()
                .filter(p -> sessionHandle.equals(p.getTestSessionHandle())).findFirst().orElse(null);
        if (player == null) {
            System.err.println("Joueur non trouvé pour le sessionHandle " + sessionHandle);
            return;
        }

        traitement(batailles, player, listLostGame, withWarning, searchTerm);
    }

    private static void traitement(List<Battle> battles, Player player, boolean listLostGame, boolean withWarning, String searchTerm) {
        List<Battle> bataillePerdu = getLostGames(battles, player.getUserId());
        List<Battle> batailleTimeout = getTimeoutGames(bataillePerdu);

        // Display
        System.out.println("------ Information ------");
        System.out.println("Pseudo: " + player.getNickname());
        System.out.println("Total games: " + battles.size());
        System.out.println("Total win or draw: " + (battles.size() - bataillePerdu.size()));
        System.out.println("Total lost: " + bataillePerdu.size());
        System.out.println();
        System.out.println("-------------------------");
        System.out.println();
        if (batailleTimeout.isEmpty()) {
            System.out.println("✅ Aucun timeout");
        } else {
            System.out.println("❌ " + batailleTimeout.size() + " timeouts :");
            batailleTimeout.forEach(battle ->
                    System.out.println("⏳ Timeout dans la game #" + getIndexGame(battles, battle.getGameId()) + " https://www.codingame.com/replay/" + battle.getGameId()));
        }

        traitementSearchTerm(battles, player, searchTerm);
        traitementWithWarning(battles, player, withWarning);

        if (listLostGame) {
            System.out.println();
            System.out.println("-------------------------");
            System.out.println();
            List<Battle> battlesLostSorted = bataillePerdu.stream()
                    .peek(battle -> {
                        List<Double> scores = battle.getGame().getScores();
                        if (scores.get(0) < scores.get(1)) {
                            battle.setEcartScore(scores.get(1) - scores.get(0));
                        } else {
                            battle.setEcartScore(scores.get(0) - scores.get(1));
                        }
                    })
                    .sorted(Comparator.comparingDouble(battle -> -battle.getEcartScore())).toList();

            System.out.println("Liste des games perdu :");
            battlesLostSorted.forEach(battle -> {
                Agent oppAgent = battle.getGame().getAgents().stream()
                        .filter(a -> a.getCodingamer().getUserId() != player.getUserId()).findFirst().orElse(null);
                System.out.println("\u27A4 Game perdu #" + getIndexGame(battles, battle.getGameId()) +
                        " [" + oppAgent.getCodingamer().getPseudo() +
                        " - rank " + oppAgent.getRank() +
                        " - elo " + String.format("%.2f", oppAgent.getScore()) + "]" +
                        " avec " + battle.getEcartScore() + " points d'écart" +
                        " https://www.codingame.com/replay/" + battle.getGameId());
            });
        }
    }

    private static long getIndexGame(List<Battle> battles, long idGame) {
        for (int i = 0; i < battles.size(); i++) {
            if (battles.get(i).getGameId() == idGame) {
                return battles.size() - i;
            }
        }
        return -1;
    }

    private static void traitementSearchTerm(List<Battle> battles, Player player, String searchTerm) {
        if (searchTerm != null) {
            System.out.println();
            System.out.println("-------------------------");
            System.out.println();
            System.out.println("Liste des games contenant: " + searchTerm);
            for (Battle battle : battles) {
                Agent agent = battle.getGame().getAgents().stream()
                        .filter(a -> a.getCodingamer().getUserId() == player.getUserId()).findFirst().orElse(null);
                List<Frame> framesWithSearchTerm = battle.getGame().getFrames().stream()
                        .filter(frame -> frame.getAgentId() == agent.getIndex())
                        .filter(frame -> frame.getSummary() != null)
                        .filter(frame -> frame.getSummary().contains(searchTerm)).toList();
                if (!framesWithSearchTerm.isEmpty()) {
                    System.out.println("\u27A4 Game #" + getIndexGame(battles, battle.getGameId()) +
                                " https://www.codingame.com/replay/" + battle.getGameId());
                }
            }
        }
    }

    private static void traitementWithWarning(List<Battle> battles, Player player, boolean withWarning) {
        if (withWarning) {
            System.out.println();
            System.out.println("-------------------------");
            System.out.println();
            System.out.println("Liste des games contenant des alertes: ");
            for (Battle battle : battles) {
                Agent agent = battle.getGame().getAgents().stream()
                        .filter(a -> a.getCodingamer().getUserId() == player.getUserId()).findFirst().orElse(null);
                List<Frame> framesWithWarning = battle.getGame().getFrames().stream()
                        .filter(frame -> frame.getAgentId() == agent.getIndex())
                        .filter(frame -> frame.getSummary() != null)
                        .filter(frame -> frame.getSummary().contains("¤RED¤")).toList();
                if (!framesWithWarning.isEmpty()) {
                    System.out.println("\u27A4 Game #" + getIndexGame(battles, battle.getGameId()) +
                            " https://www.codingame.com/replay/" + battle.getGameId());
                }
            }
        }
    }

    private static List<Battle> getLostGames(List<Battle> battles, long myUserId) {
        List<Battle> bataillePerdu = new ArrayList<>();
        for (Battle battle : battles) {
            if (battle.getPlayers().stream().anyMatch(p -> p.getPosition() != 0 && p.getUserId() == myUserId)) {
                bataillePerdu.add(battle);
            }
        }
        return bataillePerdu;
    }

    private static List<Battle> getTimeoutGames(List<Battle> bataillesPerdu) {
        List<Battle> gamesTimeout = new ArrayList<>();
        for (Battle bataille: bataillesPerdu) {
            if (bataille.getGame() != null && bataille.getGame().getScores().stream().anyMatch(score -> score == -1)) {
                gamesTimeout.add(bataille);
            }
        }
        return gamesTimeout;
    }

    private static Game getGame(long idGame) throws JsonProcessingException {
        String[] data = new String[] { ""+idGame, null };
        String jsonBody = OBJECT_MAPPER.writeValueAsString(data);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_FIND_GAME))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            // Envoie la requête et récupère la réponse
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return OBJECT_MAPPER.readValue(response.body(), Game.class);
            } else {
                System.err.println("Erreur lors de l'appel http getGame - code " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            // Gestion des erreurs
            System.err.println("Erreur lors de l'appel http getGame: " + e.getMessage());
        }
        return null;
    }

    private static List<Battle> getLastGames(String sessionHandle) throws JsonProcessingException {
        String[] data = new String[] { sessionHandle, null };
        String jsonBody = OBJECT_MAPPER.writeValueAsString(data);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_FIND_LAST_BATTLE))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            // Envoie la requête et récupère la réponse
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                List<Battle> batailles = OBJECT_MAPPER.readValue(response.body(), new TypeReference<>() {});
                for (Battle battle: batailles) {
                    try {
                        Game game = getGame(battle.getGameId());
                        if (game == null) {
                            System.err.println("Erreur lors de l'appel http getGame - code " + response.statusCode());
                            break;
                        }
                        battle.setGame(game);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                };
                return batailles;
            } else {
                System.err.println("Erreur lors de l'appel http getLastBattles - code " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            // Gestion des erreurs
            System.err.println("Erreur lors de l'appel http getLastBattles: " + e.getMessage());
        }
        return null;
    }
}
