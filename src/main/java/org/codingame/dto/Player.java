package org.codingame.dto;

import lombok.Data;

@Data
public class Player {
    long playerAgentId;
    int position;
    long userId;
    String nickname;
    String publicHandle;
    long avatar;
    String testSessionHandle;
    long submissionId;
}
