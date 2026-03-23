package com.familier.ai.repository;

import com.familier.ai.entity.Report;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface ReportRepository extends ReactiveMongoRepository<Report, String> {
    Mono<Long> countByReportedAtBetween(Instant start, Instant end);
}
