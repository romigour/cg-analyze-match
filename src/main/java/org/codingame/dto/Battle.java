package org.codingame.dto;

import lombok.Data;

import java.util.List;

@Data
public class Battle {
    List<Player> players;
    long gameId;
    boolean done;

    Game game;
    Double ecartScore;
}
