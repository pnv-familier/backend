package com.project.familierapi.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreOverviewResponse {
    private long totalUsers;
    private double userGrowth;
    private long totalFamilyGroups;
    private double groupGrowth;
}
