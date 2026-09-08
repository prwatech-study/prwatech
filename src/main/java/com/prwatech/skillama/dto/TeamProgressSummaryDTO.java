package com.prwatech.skillama.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamProgressSummaryDTO {
    private int totalMembers;
    private double averageProgress;
    private int activeMembers;
    private List<TeamMemberDTO> members;
}
