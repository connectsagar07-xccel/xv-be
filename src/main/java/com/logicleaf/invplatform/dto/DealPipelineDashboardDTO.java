package com.logicleaf.invplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealPipelineDashboardDTO {
    private long totalPipeline;
    private long starredDeals;
    private long hotDeals;
    private String totalValue;
}
