package com.logicleaf.invplatform.repository;

import com.logicleaf.invplatform.model.DealPipeline;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DealPipelineRepository extends MongoRepository<DealPipeline, String> {
    List<DealPipeline> findByInvestorId(String investorId);

    java.util.Optional<DealPipeline> findByInvestorIdAndStartupId(String investorId, String startupId);
}
