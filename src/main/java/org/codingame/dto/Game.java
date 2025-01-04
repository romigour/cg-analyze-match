package org.codingame.dto;

import lombok.Data;

import java.util.List;

@Data
public class Game {
    long gameId;
    List<Agent> agents;
    List<Frame> frames;
    List<Double> scores;
    List<Integer> ranks;

}
