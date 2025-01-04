package org.codingame.dto;

import lombok.Data;

@Data
public class Agent {
    int index;
    Codingamer codingamer;
    long agentId;
    double score;
    long rank;
    boolean valid;
}
