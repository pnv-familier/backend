package com.project.familierapi.schedule.repository;

import com.project.familierapi.schedule.domain.FamilyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FamilyEventRepository extends JpaRepository<FamilyEvent, Integer> {
    List<FamilyEvent> findByFamilyIdAndStartTimeBetweenOrderByStartTimeAsc(
            String familyId, LocalDateTime startDate, LocalDateTime endDate);
    
    List<FamilyEvent> findByFamilyIdOrderByStartTimeAsc(String familyId);
}