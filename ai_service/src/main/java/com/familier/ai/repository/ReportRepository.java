package com.familier.ai.repository;

import com.familier.ai.entity.Report;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface ReportRepository extends ReactiveMongoRepository<Report, String> {
}
