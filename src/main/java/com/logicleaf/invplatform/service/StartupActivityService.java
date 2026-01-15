package com.logicleaf.invplatform.service;

import com.logicleaf.invplatform.model.StartupActivity;
import com.logicleaf.invplatform.repository.StartupActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StartupActivityService {

    private final StartupActivityRepository startupActivityRepository;

    public void upsertActivity(String startupId, String startupName, String message) {
        Optional<StartupActivity> existingActivityOpt = startupActivityRepository.findByStartupId(startupId);

        StartupActivity activity;
        if (existingActivityOpt.isPresent()) {
            activity = existingActivityOpt.get();
            activity.setMessage(message);
            activity.setStartupName(startupName); // Update name in case it changed
            activity.setUpdatedAt(java.time.Instant.now());
        } else {
            activity = StartupActivity.builder()
                    .startupId(startupId)
                    .startupName(startupName)
                    .message(message)
                    .updatedAt(java.time.Instant.now())
                    .build();
        }
        startupActivityRepository.save(activity);
    }

    public StartupActivity getActivityByStartupId(String startupId) {
        return startupActivityRepository.findByStartupId(startupId).orElse(null);
    }

    public List<StartupActivity> getActivitiesForStartups(List<String> startupIds) {
        return startupActivityRepository.findByStartupIdIn(startupIds);
    }
}
