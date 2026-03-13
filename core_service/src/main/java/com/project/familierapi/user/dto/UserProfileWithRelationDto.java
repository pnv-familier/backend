package com.project.familierapi.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileWithRelationDto {
    private String email;
    private String fullName;
    private String birthday;
    private String gender;
    private String hobbies;
    private String relationType;
}
