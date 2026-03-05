package com.project.familierapi.post.dto;

import com.project.familierapi.post.domain.ReactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToggleReactionRequest {
    private ReactionType reactionType;
}
