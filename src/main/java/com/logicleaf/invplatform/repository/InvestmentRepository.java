package com.logicleaf.invplatform.repository;

import com.logicleaf.invplatform.model.Investment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentRepository extends MongoRepository<Investment, String> {
    List<Investment> findByInvestorId(String investorId);

    Optional<Investment> findByInvestorIdAndStartupId(String investorId, String startupId);
}
