package com.familier.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetProfileWithRelation {
    private String email;
    private String fullName;
    private String birthday;
    private String gender;
    private String hobbies;
    private String relationType;
}
