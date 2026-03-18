package com.project.familierapi.lovetask.repository;

import com.project.familierapi.lovetask.domain.LoveTask;
import com.project.familierapi.lovetask.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoveTaskRepository extends JpaRepository<LoveTask, Integer> {
    List<LoveTask> findByAssigneeIdAndStatusOrderByCreatedAtDesc(String assigneeId, TaskStatus status);
    List<LoveTask> findByFamilyIdOrderByCreatedAtDesc(String familyId);
    List<LoveTask> findByAssigneeIdOrderByCreatedAtDesc(String assigneeId);
    List<LoveTask> findBySenderIdOrderByCreatedAtDesc(String senderId);
}
