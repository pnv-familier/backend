package com.project.familierapi.admin.service;

import com.project.familierapi.admin.dto.CoreOverviewResponse;
import com.project.familierapi.auth.repository.UserRepository;
import com.project.familierapi.family.repository.FamilyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminOverviewService {

    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;

    public CoreOverviewResponse getCoreOverview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);
        LocalDateTime sixtyDaysAgo = now.minusDays(60);

        long totalUsers = userRepository.count();
        long totalFamilyGroups = familyRepository.count();

        long currentUsers = userRepository.countByCreatedAtBetween(thirtyDaysAgo, now);
        long lastUsers = userRepository.countByCreatedAtBetween(sixtyDaysAgo, thirtyDaysAgo);
        long currentGroups = familyRepository.countByCreatedAtBetween(thirtyDaysAgo, now);
        long lastGroups = familyRepository.countByCreatedAtBetween(sixtyDaysAgo, thirtyDaysAgo);

        return CoreOverviewResponse.builder()
                .totalUsers(totalUsers)
                .userGrowth(calcGrowth(currentUsers, lastUsers))
                .totalFamilyGroups(totalFamilyGroups)
                .groupGrowth(calcGrowth(currentGroups, lastGroups))
                .build();
    }

    private double calcGrowth(long current, long last) {
        if (last == 0) return current > 0 ? 100.0 : 0.0;
        return Math.round(((double) (current - last) / last) * 100 * 100.0) / 100.0;
    }
}
