package com.familier.ai.service;

import com.familier.ai.dto.ReportRequest;
import com.familier.ai.entity.Report;
import com.familier.ai.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    public Mono<Report> createReport(ReportRequest request, String reporterEmail) {
        return reportRepository.save(
                Report.builder()
                        .reason(request.getReason())
                        .reporterEmail(reporterEmail)
                        .reportedAt(Instant.now())
                        .build());
    }
}
