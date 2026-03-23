package com.project.familierapi.family.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminFamilyResponse {
    private String familyId;
    private String familyName;
    private String ownerEmail;
    private int memberCount;
    private LocalDateTime createdAt;
    private String status;
}
