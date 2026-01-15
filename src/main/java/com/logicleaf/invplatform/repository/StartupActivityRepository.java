package com.logicleaf.invplatform.repository;

import com.logicleaf.invplatform.model.StartupActivity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StartupActivityRepository extends MongoRepository<StartupActivity, String> {
    Optional<StartupActivity> findByStartupId(String startupId);

    java.util.List<StartupActivity> findByStartupIdIn(java.util.List<String> startupIds);
}
