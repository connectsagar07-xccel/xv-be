package com.logicleaf.invplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "startup_activities")
public class StartupActivity {

    @Id
    private String id;

    private String startupId;
    private String startupName;

    // The activity message (e.g., "Quarterly Report Created", "Metrics Updated")
    private String message;

    private Instant updatedAt;
}
