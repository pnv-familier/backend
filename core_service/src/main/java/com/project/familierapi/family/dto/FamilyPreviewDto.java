package com.project.familierapi.family.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyPreviewDto {
    private String familyId;
    private String familyName;
    private AdminInfo admin;
    private Integer memberCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminInfo {
        private String fullName;
        private String avatarUrl;
    }
}
