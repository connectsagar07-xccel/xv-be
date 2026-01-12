package com.logicleaf.invplatform.repository;

import com.logicleaf.invplatform.model.TimelyReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimelyReportRepository extends MongoRepository<TimelyReport, String> {
    List<TimelyReport> findByFounderUserId(String founderUserId);

    boolean existsByStartupIdAndDraftReportTrue(String startupId);

    Optional<TimelyReport> findByStartupIdAndDraftReportTrue(String startupId);

    Optional<TimelyReport> findFirstByStartupIdOrderByCreatedAtDesc(String startupId);

    List<TimelyReport> findByStartupIdOrderByCreatedAtDesc(String startupId);
}
