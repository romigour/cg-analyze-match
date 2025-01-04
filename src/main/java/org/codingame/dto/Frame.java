package org.codingame.dto;

import lombok.Data;

@Data
public class Frame {
    String gameInformation;
    String stdout;
    String stderr;
    String summary;
    long agentId;
}
