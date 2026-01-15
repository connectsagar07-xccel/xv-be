package com.logicleaf.invplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartupActivityDTO {
    private String startupId;
    private String startupName;
    private String message;
    private java.time.Instant updatedAt;
    private String timeAgo;
}
